package com.packetauditor.api;

import com.packetauditor.model.Direction;

/**
 * Pre-send / post-receive packet hooks (D12). The host or Codex can register
 * hooks to observe or lightly transform packets at the module boundary:
 *
 * <ul>
 *   <li>{@link #onPreSend(Object, Direction)} — called just before an outbound
 *       packet is written to the server; may return a replacement packet (or the
 *       same object to leave it unchanged, or {@code null} to request drop).</li>
 *   <li>{@link #onPostReceive(Object, Direction)} — called just after an inbound
 *       packet has been accepted for the client to handle (observation only).</li>
 * </ul>
 *
 * <p>Hooks run on the networking thread; keep them cheap and non-blocking.</p>
 */
public interface PacketHook {

    /**
     * @return the (possibly replaced) packet to send, the same object to leave it
     *         unchanged, or {@code null} to drop it.
     */
    default Object onPreSend(Object nativePacket, Direction direction) {
        return nativePacket;
    }

    default void onPostReceive(Object nativePacket, Direction direction) {
    }
}
