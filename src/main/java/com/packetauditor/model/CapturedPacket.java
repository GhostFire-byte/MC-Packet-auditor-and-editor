package com.packetauditor.model;

import java.util.Optional;

/**
 * A captured packet flowing through the pipeline: its {@link PacketMeta}, an
 * opaque reference to the native Minecraft packet object (as {@link Object} so
 * the core never compiles against Minecraft types), and optionally the raw
 * serialized bytes. The {@link DecodedPacket} is attached lazily by the decoder.
 *
 * <p>Instances are effectively immutable except for the lazily-attached decoded
 * view; replacing the native packet (after an edit) produces a new instance via
 * {@link #withNativePacket(Object, int)}.</p>
 */
public final class CapturedPacket {

    private final PacketMeta meta;
    private final Object nativePacket;   // the real MC packet, treated as Object
    private final byte[] rawBytes;       // nullable; present when captured raw
    private volatile DecodedPacket decoded;

    public CapturedPacket(PacketMeta meta, Object nativePacket, byte[] rawBytes) {
        if (meta == null) {
            throw new IllegalArgumentException("meta must not be null");
        }
        this.meta = meta;
        this.nativePacket = nativePacket;
        this.rawBytes = rawBytes;
    }

    public PacketMeta meta()                 { return meta; }
    public Object nativePacket()             { return nativePacket; }
    public boolean hasNativePacket()         { return nativePacket != null; }

    public Optional<byte[]> rawBytes() {
        return Optional.ofNullable(rawBytes);
    }

    public int rawLength() {
        return rawBytes == null ? -1 : rawBytes.length;
    }

    public DecodedPacket decoded()           { return decoded; }
    public boolean isDecoded()               { return decoded != null; }

    /** Attach a decoded view (set once by the decoder service). */
    public void attachDecoded(DecodedPacket d) {
        this.decoded = d;
    }

    /**
     * Produce a copy backed by a new (edited) native packet, updating the
     * serialized size in the metadata. Raw bytes are cleared because they no
     * longer match the new object; re-serialize to refresh them.
     */
    public CapturedPacket withNativePacket(Object newNative, int newSizeBytes) {
        PacketMeta newMeta = PacketMeta.builder()
                .sequence(meta.sequence())
                .direction(meta.direction())
                .phase(meta.phase())
                .packetId(meta.packetId())
                .packetName(meta.packetName())
                .packetClass(meta.packetClass())
                .sizeBytes(newSizeBytes)
                .timestampMillis(meta.timestampMillis())
                .timestampNanos(meta.timestampNanos())
                .build();
        return new CapturedPacket(newMeta, newNative, null);
    }

    /** Produce a copy backed by new raw bytes (edited at the raw level). */
    public CapturedPacket withRawBytes(byte[] newRaw) {
        PacketMeta newMeta = PacketMeta.builder()
                .sequence(meta.sequence())
                .direction(meta.direction())
                .phase(meta.phase())
                .packetId(meta.packetId())
                .packetName(meta.packetName())
                .packetClass(meta.packetClass())
                .sizeBytes(newRaw == null ? -1 : newRaw.length)
                .timestampMillis(meta.timestampMillis())
                .timestampNanos(meta.timestampNanos())
                .build();
        return new CapturedPacket(newMeta, nativePacket, newRaw);
    }

    @Override
    public String toString() {
        return meta.toString();
    }
}
