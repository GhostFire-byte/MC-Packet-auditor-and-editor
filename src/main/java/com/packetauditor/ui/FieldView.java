package com.packetauditor.ui;

/** Render-friendly view of one decoded packet field for the in-GUI editor. */
public final class FieldView {

    public final String name;
    public final String path;
    public final String type;
    public final String value;
    public final boolean editable;
    public final String notEditableReason; // nullable

    public FieldView(String name, String path, String type, String value, boolean editable,
                     String notEditableReason) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.value = value;
        this.editable = editable;
        this.notEditableReason = notEditableReason;
    }
}
