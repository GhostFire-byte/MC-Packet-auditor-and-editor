package com.packetauditor.anomaly;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.stats.StatisticsCollector;
import com.packetauditor.stats.TrafficSnapshot;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Flags traffic (bandwidth) spikes using the live bytes/second rate. Emits at
 * most once per cooldown to avoid spamming the anomaly history.
 */
public final class TrafficSpikeDetector extends AbstractDetector {

    private static final long COOLDOWN_MILLIS = 2_000;
    private final AtomicLong lastEmit = new AtomicLong();

    public TrafficSpikeDetector() {
        super("traffic-spike");
    }

    @Override
    public Anomaly inspect(CapturedPacket packet, StatisticsCollector stats) {
        TrafficSnapshot snap = stats.snapshot(3);
        double bytesPerSecond = snap.bytesInPerSecond() + snap.bytesOutPerSecond();
        // 8 MB/s at low sensitivity .. 1.5 MB/s at high sensitivity.
        double threshold = thresholdBetween(8_000_000, 1_500_000);
        if (bytesPerSecond < threshold) {
            return null;
        }
        long now = System.currentTimeMillis();
        long last = lastEmit.get();
        if (now - last < COOLDOWN_MILLIS || !lastEmit.compareAndSet(last, now)) {
            return null;
        }
        Severity sev = bytesPerSecond >= threshold * 2 ? Severity.CRITICAL : Severity.WARN;
        return anomaly(sev,
                String.format("Traffic spike: %.0f KB/s", bytesPerSecond / 1024.0),
                -1, packet.meta().packetName());
    }
}
