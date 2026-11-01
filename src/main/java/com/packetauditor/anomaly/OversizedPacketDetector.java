package com.packetauditor.anomaly;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.stats.StatisticsCollector;

/**
 * Flags unusually large packets. Thresholds are tuned for realistic 26.2 client
 * traffic so beginners are not spammed: most packets are tiny, but chunk/map/
 * registry packets can legitimately be large.
 */
public final class OversizedPacketDetector extends AbstractDetector {

    public OversizedPacketDetector() {
        super("oversized-packet");
    }

    @Override
    public Anomaly inspect(CapturedPacket packet, StatisticsCollector stats) {
        int size = packet.meta().sizeBytes();
        if (size < 0) {
            return null;
        }
        // 2 MB at low sensitivity .. 256 KB at high sensitivity.
        double warnThreshold = thresholdBetween(2_000_000, 262_144);
        double critThreshold = warnThreshold * 2.0;
        if (size >= critThreshold) {
            return anomaly(Severity.CRITICAL,
                    "Very large packet: " + size + " bytes", packet.meta().sequence(),
                    packet.meta().packetName());
        }
        if (size >= warnThreshold) {
            return anomaly(Severity.WARN,
                    "Large packet: " + size + " bytes", packet.meta().sequence(),
                    packet.meta().packetName());
        }
        return null;
    }
}
