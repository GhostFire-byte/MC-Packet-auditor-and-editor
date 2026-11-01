package com.packetauditor.anomaly;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.DecodedPacket;
import com.packetauditor.stats.StatisticsCollector;

/**
 * Flags packets that could not be decoded (malformed or unknown structure). Only
 * reports when a decoded view is already available, so it adds no decoding cost
 * on the networking thread.
 */
public final class MalformedPacketDetector extends AbstractDetector {

    public MalformedPacketDetector() {
        super("malformed-packet");
    }

    @Override
    public Anomaly inspect(CapturedPacket packet, StatisticsCollector stats) {
        if (!packet.isDecoded()) {
            return null;
        }
        DecodedPacket decoded = packet.decoded();
        if (decoded.source() == DecodedPacket.DecodeSource.RAW_ONLY) {
            String reason = decoded.notEditableReason();
            if (reason != null && reason.toLowerCase().contains("failed")) {
                return anomaly(Severity.WARN,
                        "Undecodable packet: " + reason, packet.meta().sequence(),
                        packet.meta().packetName());
            }
        }
        return null;
    }
}
