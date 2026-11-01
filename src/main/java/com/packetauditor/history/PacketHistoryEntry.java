package com.packetauditor.history;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.Direction;
import com.packetauditor.model.PacketMeta;
import com.packetauditor.model.ProtocolPhase;

/**
 * A compact, RAM-friendly history record for one captured packet (spec §12).
 * Deliberately does <b>not</b> retain the native Minecraft packet object so that
 * large histories stay bounded; it keeps metadata plus an optional decoded text
 * summary and optional raw hex.
 */
public final class PacketHistoryEntry {

    private final long sequence;
    private final long timestampMillis;
    private final Direction direction;
    private final ProtocolPhase phase;
    private final int packetId;
    private final String packetName;
    private final int sizeBytes;
    private final String summary;      // decoded display text (may be empty)
    private final String rawHex;       // nullable
    private final boolean edited;

    public PacketHistoryEntry(long sequence, long timestampMillis, Direction direction,
                              ProtocolPhase phase, int packetId, String packetName, int sizeBytes,
                              String summary, String rawHex, boolean edited) {
        this.sequence = sequence;
        this.timestampMillis = timestampMillis;
        this.direction = direction;
        this.phase = phase;
        this.packetId = packetId;
        this.packetName = packetName;
        this.sizeBytes = sizeBytes;
        this.summary = summary == null ? "" : summary;
        this.rawHex = rawHex;
        this.edited = edited;
    }

    public static PacketHistoryEntry from(CapturedPacket packet, String summary, String rawHex,
                                          boolean edited) {
        PacketMeta m = packet.meta();
        return new PacketHistoryEntry(m.sequence(), m.timestampMillis(), m.direction(), m.phase(),
                m.packetId(), m.packetName(), m.sizeBytes(), summary, rawHex, edited);
    }

    public long sequence()        { return sequence; }
    public long timestampMillis() { return timestampMillis; }
    public Direction direction()  { return direction; }
    public ProtocolPhase phase()  { return phase; }
    public int packetId()         { return packetId; }
    public String packetName()    { return packetName; }
    public int sizeBytes()        { return sizeBytes; }
    public String summary()       { return summary; }
    public String rawHex()        { return rawHex; }
    public boolean edited()       { return edited; }

    /** Rough retained-size estimate in bytes (for RAM monitoring). */
    public int estimatedBytes() {
        int base = 64; // object + primitives overhead
        base += packetName == null ? 0 : packetName.length() * 2;
        base += summary.length() * 2;
        base += rawHex == null ? 0 : rawHex.length() * 2;
        return base;
    }

    @Override
    public String toString() {
        return "#" + sequence + " " + direction.shortLabel() + " " + packetName
                + " " + sizeBytes + "B" + (edited ? " (edited)" : "");
    }
}
