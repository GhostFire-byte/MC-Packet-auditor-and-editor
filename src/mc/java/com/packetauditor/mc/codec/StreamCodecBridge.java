package com.packetauditor.mc.codec;

import com.packetauditor.codec.PacketCodec;
import com.packetauditor.model.Direction;
import com.packetauditor.model.ProtocolPhase;

/**
 * Bridges the core {@link PacketCodec} to Minecraft 26.2's native
 * {@code StreamCodec}-based serialization (D7).
 *
 * <p><b>Important design note:</b> because the module forwards the <i>native
 * {@link net.minecraft.network.protocol.Packet} object</i> (edited in place by a
 * curated adapter via record reconstruction), Minecraft's own encoder serializes
 * it on the wire — so this bridge is <i>not</i> needed just to forward edits. It
 * is used for the raw-bytes viewer/editor and precise packet sizing.</p>
 *
 * <p>The active per-phase codec is version/connection specific. Rather than
 * hardcode it, the host plugs a {@link Resolver} that knows how to obtain the
 * current {@code ProtocolInfo}/{@code StreamCodec} and a
 * {@code RegistryFriendlyByteBuf}. When no resolver is set, size/id are reported
 * as unknown (the UI degrades gracefully) and raw encode/decode return null.</p>
 */
public final class StreamCodecBridge implements PacketCodec {

    /**
     * Host-provided hook that performs the actual native (de)serialization using
     * the connection's current protocol codec. All methods may return
     * -1/null/empty when unavailable.
     */
    public interface Resolver {
        /** Serialize a packet to wire bytes, or null if unavailable. */
        byte[] encode(Object packet, Direction direction, ProtocolPhase phase);

        /** Decode wire bytes to a native packet, or null if unavailable. */
        Object decode(byte[] body, Direction direction, ProtocolPhase phase);

        /** The packet id within the active protocol, or -1 if unknown. */
        int packetId(Object packet, Direction direction, ProtocolPhase phase);
    }

    private volatile Resolver resolver;

    public void setResolver(Resolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public byte[] encode(Object nativePacket, Direction direction, ProtocolPhase phase) {
        Resolver r = resolver;
        return r == null ? null : r.encode(nativePacket, direction, phase);
    }

    @Override
    public Object decode(byte[] body, Direction direction, ProtocolPhase phase) {
        Resolver r = resolver;
        return r == null ? null : r.decode(body, direction, phase);
    }

    public int packetId(Object nativePacket, Direction direction, ProtocolPhase phase) {
        Resolver r = resolver;
        return r == null ? -1 : r.packetId(nativePacket, direction, phase);
    }

    /** Precise serialized size, or -1 if unavailable. */
    public int encodedSize(Object nativePacket, Direction direction, ProtocolPhase phase) {
        byte[] b = encode(nativePacket, direction, phase);
        return b == null ? -1 : b.length;
    }
}
