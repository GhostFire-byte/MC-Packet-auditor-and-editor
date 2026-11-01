package com.packetauditor.anomaly;

import com.packetauditor.codec.DecoderService;
import com.packetauditor.model.CapturedPacket;
import com.packetauditor.stats.StatisticsCollector;
import com.packetauditor.util.AuditorLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Runs the enabled {@link AnomalyDetector}s against packets and keeps a bounded
 * anomaly history plus per-packet severity markers for the UI (spec §11).
 *
 * <p>Strictly diagnostic: it never alters, blocks or drops traffic. It is meant
 * to be driven from a background thread (not the networking thread) by the
 * pipeline.</p>
 */
public final class AnomalyDetectionService {

    private static final AuditorLog LOG = AuditorLog.of(AnomalyDetectionService.class);
    private static final int MAX_HISTORY = 2_000;

    private final List<AnomalyDetector> detectors = new CopyOnWriteArrayList<>();
    private final Deque<Anomaly> history = new ArrayDeque<>();
    private final Object historyLock = new Object();
    private final ConcurrentHashMap<Long, Severity> markers = new ConcurrentHashMap<>();
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    private final DecoderService decoder; // nullable; used so custom rules can read fields
    private volatile Consumer<Anomaly> listener = a -> { };

    public AnomalyDetectionService(DecoderService decoder) {
        this.decoder = decoder;
    }

    /** Register the default built-in detectors. */
    public void registerBuiltIns() {
        detectors.add(new OversizedPacketDetector());
        detectors.add(new TrafficSpikeDetector());
        detectors.add(new PacketFloodDetector());
        detectors.add(new MalformedPacketDetector());
    }

    public void addDetector(AnomalyDetector d) {
        if (d != null) {
            detectors.add(d);
        }
    }

    public void removeDetector(String id) {
        detectors.removeIf(d -> d.id().equals(id));
    }

    public List<AnomalyDetector> detectors() {
        return detectors;
    }

    public Optional<AnomalyDetector> detector(String id) {
        return detectors.stream().filter(d -> d.id().equals(id)).findFirst();
    }

    public boolean enabled() {
        return enabled.get();
    }

    public void setEnabled(boolean on) {
        enabled.set(on);
    }

    public void setListener(Consumer<Anomaly> listener) {
        this.listener = listener == null ? a -> { } : listener;
    }

    /** Run all enabled detectors against a packet, recording any anomalies found. */
    public List<Anomaly> inspect(CapturedPacket packet, StatisticsCollector stats) {
        if (!enabled.get()) {
            return List.of();
        }
        // Ensure a decoded view is available for field-based custom rules.
        if (decoder != null && !packet.isDecoded()) {
            try {
                decoder.decode(packet);
            } catch (Throwable ignored) {
                // decoding failures are themselves surfaced by MalformedPacketDetector
            }
        }

        List<Anomaly> found = new ArrayList<>();
        for (AnomalyDetector d : detectors) {
            if (!d.enabled()) {
                continue;
            }
            try {
                Anomaly a = d.inspect(packet, stats);
                if (a != null) {
                    record(a);
                    found.add(a);
                }
            } catch (Throwable t) {
                LOG.warn("Detector '" + d.id() + "' failed", t);
            }
        }
        return found;
    }

    private void record(Anomaly a) {
        synchronized (historyLock) {
            history.addLast(a);
            while (history.size() > MAX_HISTORY) {
                history.pollFirst();
            }
        }
        if (a.packetSequence() >= 0) {
            markers.merge(a.packetSequence(), a.severity(),
                    (oldSev, newSev) -> newSev.ordinal() > oldSev.ordinal() ? newSev : oldSev);
        }
        try {
            listener.accept(a);
        } catch (Throwable ignored) {
        }
    }

    /** Highest severity recorded for a packet, or null if none. */
    public Severity markerFor(long packetSequence) {
        return markers.get(packetSequence);
    }

    public List<Anomaly> history() {
        synchronized (historyLock) {
            return new ArrayList<>(history);
        }
    }

    public void clearHistory() {
        synchronized (historyLock) {
            history.clear();
        }
        markers.clear();
    }
}
