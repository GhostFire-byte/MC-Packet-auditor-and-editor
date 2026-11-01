package com.packetauditor.codec;

import com.packetauditor.model.DecodedPacket;
import com.packetauditor.model.PacketField;

/**
 * A curated, fully-understood adapter for a specific family of packets. Provides
 * rich, editable, validated field views and rebuilds a new native packet when a
 * field is changed (D7).
 *
 * <p>Curated adapters are implemented in the {@code src/mc} adapter for the
 * prioritized packet types (movement/position, attack/entity interaction,
 * inventory clicks, chat, container/GUI). Everything else falls back to the
 * core {@link ReflectiveDecoder}.</p>
 */
public interface PacketEditAdapter {

    /** True if this adapter handles the given native packet object. */
    boolean supports(Object nativePacket);

    /** A short human-readable label for the packet family (e.g. "Player Position"). */
    String label();

    /** Decode the native packet into an editable structured view. */
    DecodedPacket decode(Object nativePacket);

    /**
     * Apply an edit to one field and return a NEW native packet instance with
     * the change applied (packets are immutable records; never mutate in place).
     *
     * @param nativePacket the current native packet
     * @param fieldPath    dotted path of the field being edited
     * @param newValue     the already-parsed, already-validated new value
     * @return the new native packet
     * @throws EditException if the edit cannot be applied
     */
    Object applyEdit(Object nativePacket, String fieldPath, Object newValue) throws EditException;

    /**
     * Validate a proposed value for a field without applying it.
     *
     * @return null if valid, otherwise a human-readable validation error.
     */
    default String validate(Object nativePacket, PacketField field, Object proposedValue) {
        return null;
    }

    /** Thrown when a structured edit cannot be applied. */
    class EditException extends Exception {
        private static final long serialVersionUID = 1L;
        public EditException(String message) {
            super(message);
        }
        public EditException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
