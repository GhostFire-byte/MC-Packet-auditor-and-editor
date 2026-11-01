package com.packetauditor.ui;

import java.util.List;

/** Render-friendly detail of a selected packet for the in-GUI inspector/editor. */
public final class PacketDetail {

    public final long sequence;
    public final String name;
    public final String directionLabel;
    public final String phase;
    public final int size;
    public final String decodeSource;   // CURATED_ADAPTER / REFLECTIVE / RAW_ONLY
    public final boolean editable;
    public final String notEditableReason; // nullable
    public final List<FieldView> fields;

    public PacketDetail(long sequence, String name, String directionLabel, String phase, int size,
                        String decodeSource, boolean editable, String notEditableReason,
                        List<FieldView> fields) {
        this.sequence = sequence;
        this.name = name;
        this.directionLabel = directionLabel;
        this.phase = phase;
        this.size = size;
        this.decodeSource = decodeSource;
        this.editable = editable;
        this.notEditableReason = notEditableReason;
        this.fields = fields;
    }
}
