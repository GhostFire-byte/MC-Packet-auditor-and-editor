package com.packetauditor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Structured, framework-free view of a decoded packet: an ordered list of
 * top-level {@link PacketField}s plus flags describing how confidently the
 * packet could be decoded / edited.
 */
public final class DecodedPacket {

    /** How the packet was decoded. */
    public enum DecodeSource {
        /** A curated, fully-understood adapter for this packet type. */
        CURATED_ADAPTER,
        /** The generic reflective decoder (field names from the deobfuscated jar). */
        REFLECTIVE,
        /** Could not be decoded; only raw bytes are available. */
        RAW_ONLY
    }

    private final DecodeSource source;
    private final List<PacketField> fields;
    private final boolean editable;
    private final String notEditableReason;

    private DecodedPacket(DecodeSource source, List<PacketField> fields,
                          boolean editable, String notEditableReason) {
        this.source = source;
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
        this.editable = editable;
        this.notEditableReason = notEditableReason;
    }

    public DecodeSource source()           { return source; }
    public List<PacketField> fields()      { return fields; }
    public boolean editable()              { return editable; }
    public String notEditableReason()      { return notEditableReason; }

    public Optional<PacketField> field(String path) {
        return find(fields, path);
    }

    private static Optional<PacketField> find(List<PacketField> fields, String path) {
        for (PacketField f : fields) {
            if (f.path().equals(path)) {
                return Optional.of(f);
            }
            Optional<PacketField> nested = find(f.children(), path);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }

    public static DecodedPacket curated(List<PacketField> fields, boolean editable, String reason) {
        return new DecodedPacket(DecodeSource.CURATED_ADAPTER, fields, editable, reason);
    }

    public static DecodedPacket reflective(List<PacketField> fields) {
        // Reflective decode is inspect-only by default; curated adapters add edit support.
        return new DecodedPacket(DecodeSource.REFLECTIVE, fields, false,
                "Reflectively decoded; no curated editable adapter for this packet type.");
    }

    public static DecodedPacket rawOnly(String reason) {
        return new DecodedPacket(DecodeSource.RAW_ONLY, Collections.emptyList(), false, reason);
    }
}
