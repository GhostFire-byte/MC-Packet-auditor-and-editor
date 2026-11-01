package com.packetauditor.ui;

import com.packetauditor.anomaly.Severity;

/**
 * A lightweight, render-friendly view of one captured packet for the UI list.
 * Deliberately holds no native packet object — just what the list needs to draw.
 */
public final class PacketRow {

    public final long sequence;
    public final boolean inbound;
    public final String directionLabel;
    public final String name;
    public final int size;
    public final String phase;
    public final long timestampMillis;
    public final boolean edited;
    public final Severity severity;   // nullable

    public PacketRow(long sequence, boolean inbound, String directionLabel, String name, int size,
                     String phase, long timestampMillis, boolean edited, Severity severity) {
        this.sequence = sequence;
        this.inbound = inbound;
        this.directionLabel = directionLabel;
        this.name = name;
        this.size = size;
        this.phase = phase;
        this.timestampMillis = timestampMillis;
        this.edited = edited;
        this.severity = severity;
    }
}
