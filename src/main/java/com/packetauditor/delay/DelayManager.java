package com.packetauditor.delay;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.DecodedPacket;
import com.packetauditor.pipeline.PacketTransport;
import com.packetauditor.util.AuditorLog;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages delay profiles and computes/schedules per-packet delays (spec §9).
 * Delaying a packet is "holding with an auto-release timer": the packet is
 * scheduled on the event loop via {@link PacketTransport#schedule} (D4) — no
 * sleeping threads. A global ceiling bounds the worst-case delay (timeout
 * protection).
 */
public final class DelayManager {

    private static final AuditorLog LOG = AuditorLog.of(DelayManager.class);

    private final List<DelayProfile> profiles = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    private volatile boolean enabled = false;
    private volatile long globalCeilingMillis = 10_000; // absolute safety ceiling

    // Live statistics
    private final AtomicLong totalDelayed = new AtomicLong();
    private final AtomicLong activeDelayed = new AtomicLong();
    private final AtomicLong totalDelayMillis = new AtomicLong();

    public boolean enabled()                      { return enabled; }
    public void setEnabled(boolean enabled)       { this.enabled = enabled; }
    public long globalCeilingMillis()             { return globalCeilingMillis; }
    public void setGlobalCeilingMillis(long v)    { this.globalCeilingMillis = Math.max(0, v); }

    public void addProfile(DelayProfile p)        { if (p != null) profiles.add(p); }
    public void removeProfile(String id)          { profiles.removeIf(p -> p.id().equals(id)); }
    public List<DelayProfile> profiles()          { return profiles; }

    /**
     * Compute the delay (ms) to apply to a packet from the first matching enabled
     * profile, clamped to the global ceiling. Returns 0 if no delay applies.
     */
    public long computeDelayMillis(CapturedPacket packet, DecodedPacket decoded) {
        if (!enabled) {
            return 0;
        }
        for (DelayProfile p : profiles) {
            if (!p.enabled() || !p.appliesTo(packet.meta().direction())) {
                continue;
            }
            if (p.packetFilter() != null && !p.packetFilter().matches(packet, decoded)) {
                continue;
            }
            long delay = p.computeDelayMillis(random);
            if (globalCeilingMillis > 0) {
                delay = Math.min(delay, globalCeilingMillis);
            }
            return delay;
        }
        return 0;
    }

    /**
     * Schedule delivery of a packet after {@code delayMillis} using the transport's
     * event-loop scheduler, updating live statistics.
     */
    public void scheduleDelivery(CapturedPacket packet, long delayMillis, PacketTransport transport) {
        if (delayMillis <= 0) {
            transport.deliver(packet);
            return;
        }
        totalDelayed.incrementAndGet();
        activeDelayed.incrementAndGet();
        totalDelayMillis.addAndGet(delayMillis);
        transport.schedule(() -> {
            try {
                transport.deliver(packet);
            } catch (Throwable t) {
                LOG.error("Delayed delivery failed for " + packet.meta().packetName(), t);
            } finally {
                activeDelayed.decrementAndGet();
            }
        }, delayMillis);
    }

    public DelayStats stats() {
        long total = totalDelayed.get();
        long avg = total == 0 ? 0 : totalDelayMillis.get() / total;
        return new DelayStats(total, activeDelayed.get(), avg);
    }

    /** Immutable live delay statistics. */
    public static final class DelayStats {
        private final long totalDelayed;
        private final long activeDelayed;
        private final long averageDelayMillis;

        public DelayStats(long totalDelayed, long activeDelayed, long averageDelayMillis) {
            this.totalDelayed = totalDelayed;
            this.activeDelayed = activeDelayed;
            this.averageDelayMillis = averageDelayMillis;
        }

        public long totalDelayed()       { return totalDelayed; }
        public long activeDelayed()      { return activeDelayed; }
        public long averageDelayMillis() { return averageDelayMillis; }
    }
}
