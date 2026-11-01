package com.packetauditor.codec;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.Direction;
import com.packetauditor.model.ProtocolPhase;

/**
 * Serialization bridge between the framework-free core and Minecraft's native
 * {@code StreamCodec}-based (de)serialization. Implemented in the {@code src/mc}
 * adapter ({@code com.packetauditor.mc.codec}) against the real MC 26.2 classes;
 * the core only ever sees this interface.
 *
 * <p>All methods operate on the native packet as {@link Object} so the core has
 * no compile dependency on Minecraft. Editing follows the approved model (D7):
 * structured edits rebuild a new native packet and re-serialize through the
 * registered codec; raw edits decode&rarr;validate&rarr;re-encode.</p>
 */
public interface PacketCodec {

    /**
     * Serialize a native packet to its on-wire bytes (the packet body, i.e. the
     * VarInt packet id followed by the codec-written payload, matching what the
     * vanilla encoder would produce for the given phase/direction).
     *
     * @return the serialized bytes, or {@code null} if this packet cannot be
     *         serialized by the adapter.
     */
    byte[] encode(Object nativePacket, Direction direction, ProtocolPhase phase);

    /**
     * Decode on-wire bytes back into a native packet object for the given
     * phase/direction.
     *
     * @return the native packet, or {@code null} if the bytes cannot be decoded.
     */
    Object decode(byte[] body, Direction direction, ProtocolPhase phase);

    /**
     * Re-serialize a {@link CapturedPacket} whose native packet was edited and
     * return the fresh byte length (used to refresh metadata). Implementations
     * must validate before producing bytes.
     *
     * @return serialized length in bytes, or -1 if it cannot be serialized.
     */
    default int serializedLength(Object nativePacket, Direction direction, ProtocolPhase phase) {
        byte[] b = encode(nativePacket, direction, phase);
        return b == null ? -1 : b.length;
    }
}
