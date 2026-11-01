package com.packetauditor.model;

import java.util.Objects;

/**
 * A stable, framework-free identity + lightweight metadata for a single packet.
 * Captured cheaply on the networking thread; never holds Minecraft types.
 */
public final class PacketMeta {

    private final long sequence;
    private final Direction direction;
    private final ProtocolPhase phase;
    private final int packetId;          // -1 when unknown
    private final String packetName;     // simple class name, human readable
    private final String packetClass;    // fully-qualified class name
    private final int sizeBytes;         // serialized size, -1 when unknown
    private final long timestampMillis;
    private final long timestampNanos;

    private PacketMeta(Builder b) {
        this.sequence = b.sequence;
        this.direction = Objects.requireNonNull(b.direction, "direction");
        this.phase = b.phase == null ? ProtocolPhase.UNKNOWN : b.phase;
        this.packetId = b.packetId;
        this.packetName = b.packetName == null ? "Unknown" : b.packetName;
        this.packetClass = b.packetClass == null ? "" : b.packetClass;
        this.sizeBytes = b.sizeBytes;
        this.timestampMillis = b.timestampMillis;
        this.timestampNanos = b.timestampNanos;
    }

    public long sequence()            { return sequence; }
    public Direction direction()      { return direction; }
    public ProtocolPhase phase()      { return phase; }
    public int packetId()             { return packetId; }
    public String packetName()        { return packetName; }
    public String packetClass()       { return packetClass; }
    public int sizeBytes()            { return sizeBytes; }
    public long timestampMillis()     { return timestampMillis; }
    public long timestampNanos()      { return timestampNanos; }

    public boolean hasKnownId()   { return packetId >= 0; }
    public boolean hasKnownSize() { return sizeBytes >= 0; }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "#" + sequence + " " + direction.shortLabel() + " " + packetName
                + (hasKnownId() ? " (id=" + packetId + ")" : "")
                + " [" + phase + "]"
                + (hasKnownSize() ? " " + sizeBytes + "B" : "");
    }

    public static final class Builder {
        private long sequence;
        private Direction direction;
        private ProtocolPhase phase;
        private int packetId = -1;
        private String packetName;
        private String packetClass;
        private int sizeBytes = -1;
        private long timestampMillis = System.currentTimeMillis();
        private long timestampNanos = System.nanoTime();

        public Builder sequence(long v)            { this.sequence = v; return this; }
        public Builder direction(Direction v)      { this.direction = v; return this; }
        public Builder phase(ProtocolPhase v)      { this.phase = v; return this; }
        public Builder packetId(int v)             { this.packetId = v; return this; }
        public Builder packetName(String v)        { this.packetName = v; return this; }
        public Builder packetClass(String v)       { this.packetClass = v; return this; }
        public Builder sizeBytes(int v)            { this.sizeBytes = v; return this; }
        public Builder timestampMillis(long v)     { this.timestampMillis = v; return this; }
        public Builder timestampNanos(long v)      { this.timestampNanos = v; return this; }

        public PacketMeta build() {
            return new PacketMeta(this);
        }
    }
}
