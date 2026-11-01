package com.packetauditor.codec;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.DecodedPacket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Orchestrates packet decoding (D7): first tries a curated {@link PacketEditAdapter}
 * (rich + editable), then the generic {@link ReflectiveDecoder} (inspect-only),
 * then falls back to raw-only. The decoded view is cached on the
 * {@link CapturedPacket}.
 *
 * <p>Curated adapters are registered by the {@code src/mc} integration; with no
 * adapters registered (e.g. in unit tests), decoding is purely reflective.</p>
 */
public final class DecoderService {

    private final List<PacketEditAdapter> adapters = new CopyOnWriteArrayList<>();
    private final ReflectiveDecoder reflective = new ReflectiveDecoder();

    public void registerAdapter(PacketEditAdapter adapter) {
        if (adapter != null) {
            adapters.add(adapter);
        }
    }

    public List<PacketEditAdapter> adapters() {
        return adapters;
    }

    /** Find the curated adapter for a native packet, if any. */
    public PacketEditAdapter adapterFor(Object nativePacket) {
        if (nativePacket == null) {
            return null;
        }
        for (PacketEditAdapter a : adapters) {
            try {
                if (a.supports(nativePacket)) {
                    return a;
                }
            } catch (Throwable ignored) {
                // A broken adapter must never break decoding.
            }
        }
        return null;
    }

    /**
     * Decode (and cache) the structured view for a captured packet.
     */
    public DecodedPacket decode(CapturedPacket packet) {
        if (packet.isDecoded()) {
            return packet.decoded();
        }
        DecodedPacket result = decodeFresh(packet);
        packet.attachDecoded(result);
        return result;
    }

    private DecodedPacket decodeFresh(CapturedPacket packet) {
        Object nativePacket = packet.nativePacket();
        if (nativePacket == null) {
            return DecodedPacket.rawOnly(packet.rawBytes().isPresent()
                    ? "Only raw bytes captured for this packet."
                    : "No packet data available.");
        }
        PacketEditAdapter adapter = adapterFor(nativePacket);
        if (adapter != null) {
            try {
                return adapter.decode(nativePacket);
            } catch (Throwable t) {
                // Fall through to reflective on adapter failure.
            }
        }
        return reflective.decode(nativePacket);
    }
}
