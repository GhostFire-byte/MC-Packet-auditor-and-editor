package com.packetauditor.pipeline;

import com.packetauditor.anomaly.AnomalyDetectionService;
import com.packetauditor.codec.DecoderService;
import com.packetauditor.delay.DelayManager;
import com.packetauditor.history.PacketHistory;
import com.packetauditor.history.PacketHistoryEntry;
import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.DecodedPacket;
import com.packetauditor.model.Direction;
import com.packetauditor.model.PacketMeta;
import com.packetauditor.model.ProtocolPhase;
import com.packetauditor.netsim.NetworkSimulator;
import com.packetauditor.perf.PerformanceManager;
import com.packetauditor.protect.OperationType;
import com.packetauditor.protect.ProtectionPolicy;
import com.packetauditor.queue.InterceptionQueue;
import com.packetauditor.rules.EditResult;
import com.packetauditor.rules.EditingRuleEngine;
import com.packetauditor.stats.StatisticsCollector;
import com.packetauditor.store.SqlitePacketStore;
import com.packetauditor.util.AuditorLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The central processing pipeline (ties together D4/D6/D7/D9 behaviour). The
 * Netty handler calls {@link #process} for every inbound/outbound packet on the
 * event loop; the method is kept light (lazy decode, background analysis) and
 * returns a {@link CaptureResult} telling the handler whether to forward, replace
 * or suppress the packet.
 *
 * <p>Order of operations: auto-edit → simulated loss → discard rule → intercept
 * (hold) → delay/throttle → forward. Every control step is gated by the
 * {@link ProtectionPolicy}; protected packets bypass loss/hold/delay unless
 * "Unlock ALL" is on (D14).</p>
 */
public final class PacketPipeline {

    private static final AuditorLog LOG = AuditorLog.of(PacketPipeline.class);
    private static final int RECENT_LIVE_MAX = 2_000;

    private final ProtectionPolicy protection;
    private final DecoderService decoder;
    private final StatisticsCollector stats;
    private final PacketHistory history;
    private final AnomalyDetectionService anomaly;
    private final InterceptionQueue queue;
    private final DelayManager delay;
    private final NetworkSimulator netsim;
    private final EditingRuleEngine rules;
    private final PerformanceManager perf;

    private final MatchRuleList interceptRules = new MatchRuleList();
    private final MatchRuleList discardRules = new MatchRuleList();

    private volatile PacketTransport transport;
    private volatile SqlitePacketStore sqlite;    // nullable
    private volatile boolean captureEnabled = true;
    private volatile boolean diskPersistence = false;

    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean active = new AtomicBoolean(false);

    private final Deque<CapturedPacket> recentLive = new ArrayDeque<>();
    private final Object recentLock = new Object();
    private volatile Consumer<CapturedPacket> captureListener = p -> { };

    public PacketPipeline(ProtectionPolicy protection, DecoderService decoder,
                          StatisticsCollector stats, PacketHistory history,
                          AnomalyDetectionService anomaly, InterceptionQueue queue,
                          DelayManager delay, NetworkSimulator netsim,
                          EditingRuleEngine rules, PerformanceManager perf) {
        this.protection = protection;
        this.decoder = decoder;
        this.stats = stats;
        this.history = history;
        this.anomaly = anomaly;
        this.queue = queue;
        this.delay = delay;
        this.netsim = netsim;
        this.rules = rules;
        this.perf = perf;
    }

    // ----- configuration / wiring -----
    public void setTransport(PacketTransport transport) { this.transport = transport; }
    public void setSqlite(SqlitePacketStore sqlite)      { this.sqlite = sqlite; }
    public void setCaptureEnabled(boolean enabled)       { this.captureEnabled = enabled; }
    public boolean captureEnabled()                      { return captureEnabled; }
    public void setDiskPersistence(boolean on)           { this.diskPersistence = on; }
    public boolean diskPersistence()                     { return diskPersistence; }
    public void setActive(boolean active)                { this.active.set(active); }
    public boolean isActive()                            { return active.get(); }
    public void setCaptureListener(Consumer<CapturedPacket> l) { this.captureListener = l == null ? p -> { } : l; }

    public MatchRuleList interceptRules() { return interceptRules; }
    public MatchRuleList discardRules()   { return discardRules; }

    /**
     * Process one packet on the networking event loop and decide its fate.
     *
     * @param nativePacket the native MC packet object
     * @param direction    inbound/outbound
     * @param packetId     packet id, or -1 if unknown
     * @param sizeBytes    serialized size, or -1 if unknown
     * @param phase        protocol phase
     * @param rawBytes     serialized bytes, or null if not captured
     */
    public CaptureResult process(Object nativePacket, Direction direction, int packetId,
                                 int sizeBytes, ProtocolPhase phase, byte[] rawBytes) {
        if (!captureEnabled) {
            return CaptureResult.forwardOriginal();
        }
        try {
            return processGuarded(nativePacket, direction, packetId, sizeBytes, phase, rawBytes);
        } catch (Throwable t) {
            // Fail-safe: a bug in capture/edit/analysis must NEVER break the
            // connection. Forward the original packet unchanged.
            LOG.warn("Packet processing error; forwarding original packet unchanged: " + t, t);
            return CaptureResult.forwardOriginal();
        }
    }

    private CaptureResult processGuarded(Object nativePacket, Direction direction, int packetId,
                                         int sizeBytes, ProtocolPhase phase, byte[] rawBytes) {
        long seq = sequence.incrementAndGet();
        String name = nativePacket == null ? "Unknown" : nativePacket.getClass().getSimpleName();
        String cls = nativePacket == null ? "" : nativePacket.getClass().getName();

        PacketMeta meta = PacketMeta.builder()
                .sequence(seq).direction(direction).phase(phase)
                .packetId(packetId).packetName(name).packetClass(cls)
                .sizeBytes(sizeBytes)
                .build();

        stats.record(direction, name, Math.max(0, sizeBytes));

        CapturedPacket captured = new CapturedPacket(meta, nativePacket, rawBytes);
        addRecentLive(captured);
        try {
            captureListener.accept(captured);
        } catch (Throwable ignored) {
        }

        boolean needDecode = rules.hasActiveRules() || !interceptRules.isEmpty()
                || !discardRules.isEmpty();
        DecodedPacket decoded = needDecode ? safeDecode(captured) : null;

        // 1. Auto-edit (protection-gated inside the engine).
        Object current = nativePacket;
        boolean edited = false;
        if (rules.hasActiveRules()) {
            EditResult er = rules.applyMatchingRules(captured);
            if (er.applied()) {
                current = er.resultingNativePacket();
                edited = true;
            }
        }
        CapturedPacket effective = edited ? captured.withNativePacket(current, sizeBytes) : captured;

        CaptureResult result;

        // 2. Simulated packet loss (netsim).
        if (netsim.enabled() && allowed(meta, OperationType.SIMULATE_LOSS)
                && netsim.shouldDrop(direction)) {
            result = CaptureResult.suppress("simulated loss");
        }
        // 3. Discard rule.
        else if (!discardRules.isEmpty() && discardRules.matchesAny(captured, decoded)
                && allowed(meta, OperationType.DISCARD)) {
            result = CaptureResult.suppress("discarded by rule");
        }
        // 4. Intercept (hold) rule.
        else if (transport != null && !interceptRules.isEmpty()
                && interceptRules.matchesAny(captured, decoded)
                && allowed(meta, OperationType.INTERCEPT)) {
            queue.hold(effective, "rule intercept");
            result = CaptureResult.suppress("held in interception queue");
        }
        // 5. Delay / throttle.
        else {
            long throttle = netsim.enabled() && allowed(meta, OperationType.THROTTLE)
                    ? netsim.throttleDelayMillis(direction, Math.max(0, sizeBytes)) : 0;
            long profileDelay = allowed(meta, OperationType.DELAY)
                    ? delay.computeDelayMillis(captured, decoded) : 0;
            long total = throttle + profileDelay;
            if (transport != null && total > 0) {
                delay.scheduleDelivery(effective, total, transport);
                result = CaptureResult.suppress("delayed " + total + "ms");
            } else {
                result = edited ? CaptureResult.forwardReplacement(current)
                        : CaptureResult.forwardOriginal();
            }
        }

        scheduleBackground(effective, edited);
        return result;
    }

    private boolean allowed(PacketMeta meta, OperationType op) {
        return protection.evaluate(meta, op).allowed();
    }

    private DecodedPacket safeDecode(CapturedPacket packet) {
        try {
            return decoder.decode(packet);
        } catch (Throwable t) {
            return null;
        }
    }

    private void scheduleBackground(CapturedPacket packet, boolean edited) {
        perf.submit(() -> {
            try {
                DecodedPacket decoded = decoder.decode(packet);
                if (anomaly.enabled() && !perf.shouldThrottleAnalysis()) {
                    anomaly.inspect(packet, stats);
                }
                String summary = summarize(decoded);
                PacketHistoryEntry entry = PacketHistoryEntry.from(packet, summary, null, edited);
                history.add(entry);
                SqlitePacketStore db = sqlite;
                if (diskPersistence && db != null) {
                    db.insertPacketAsync(entry);
                }
            } catch (Throwable t) {
                LOG.debug("Background post-processing failed: " + t.getMessage());
            }
        });
    }

    private String summarize(DecodedPacket decoded) {
        if (decoded == null || decoded.fields().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (var f : decoded.fields()) {
            if (count++ > 0) {
                sb.append(", ");
            }
            sb.append(f.name()).append('=').append(f.displayValue());
            if (count >= 6) {
                sb.append(", \u2026");
                break;
            }
        }
        return sb.toString();
    }

    private void addRecentLive(CapturedPacket packet) {
        synchronized (recentLock) {
            recentLive.addLast(packet);
            while (recentLive.size() > RECENT_LIVE_MAX) {
                recentLive.pollFirst();
            }
        }
    }

    /** Most recent live packets (with native objects) for interactive actions. */
    public List<CapturedPacket> recentLive(int limit) {
        synchronized (recentLock) {
            int n = Math.min(limit <= 0 ? recentLive.size() : limit, recentLive.size());
            List<CapturedPacket> out = new ArrayList<>(n);
            var it = recentLive.descendingIterator();
            while (it.hasNext() && out.size() < n) {
                out.add(it.next());
            }
            return out;
        }
    }

    public CapturedPacket findBySequence(long seq) {
        synchronized (recentLock) {
            for (CapturedPacket p : recentLive) {
                if (p.meta().sequence() == seq) {
                    return p;
                }
            }
        }
        return null;
    }

    public long totalProcessed() {
        return sequence.get();
    }
}
