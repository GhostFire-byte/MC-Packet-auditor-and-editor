package com.packetauditor.anomaly;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.stats.StatisticsCollector;

/**
 * A single anomaly detector (spec §11). Detectors are individually toggleable
 * and have adjustable sensitivity (0.0 = least sensitive .. 1.0 = most sensitive).
 * They are diagnostic only and must never mutate the packet.
 */
public interface AnomalyDetector {

    String id();

    boolean enabled();

    void setEnabled(boolean enabled);

    /** 0.0 (least sensitive) .. 1.0 (most sensitive). */
    double sensitivity();

    void setSensitivity(double sensitivity);

    /**
     * Inspect a captured packet (with live stats context) and return an anomaly,
     * or {@code null} if nothing unusual was found.
     */
    Anomaly inspect(CapturedPacket packet, StatisticsCollector stats);
}
