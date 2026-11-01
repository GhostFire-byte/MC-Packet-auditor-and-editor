package com.packetauditor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One node in a decoded packet's structured field tree.
 *
 * <p>A field carries a human-readable name/path, its {@link FieldType}, the
 * current decoded value (as a plain Java value or a child list), and whether it
 * is editable. Container fields ({@link FieldType#COMPOUND}/{@link FieldType#LIST})
 * hold {@link #children()} instead of a scalar value.</p>
 */
public final class PacketField {

    private final String name;
    private final String path;            // dotted path from the root
    private final FieldType type;
    private final Object value;           // null for containers
    private final boolean editable;
    private final String notEditableReason; // null when editable
    private final List<PacketField> children;

    private PacketField(Builder b) {
        this.name = Objects.requireNonNull(b.name, "name");
        this.path = b.path == null ? b.name : b.path;
        this.type = b.type == null ? FieldType.UNKNOWN : b.type;
        this.value = b.value;
        this.editable = b.editable;
        this.notEditableReason = b.notEditableReason;
        this.children = b.children == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(b.children));
    }

    public String name()               { return name; }
    public String path()               { return path; }
    public FieldType type()            { return type; }
    public Object value()              { return value; }
    public boolean editable()          { return editable; }
    public String notEditableReason()  { return notEditableReason; }
    public List<PacketField> children(){ return children; }

    public boolean isContainer()       { return type.isContainer(); }

    public String displayValue() {
        if (isContainer()) {
            return type == FieldType.LIST
                    ? "[" + children.size() + " items]"
                    : "{" + children.size() + " fields}";
        }
        return String.valueOf(value);
    }

    public static Builder builder(String name) {
        return new Builder().name(name);
    }

    public static PacketField scalar(String name, FieldType type, Object value, boolean editable) {
        return builder(name).type(type).value(value).editable(editable).build();
    }

    public static final class Builder {
        private String name;
        private String path;
        private FieldType type;
        private Object value;
        private boolean editable;
        private String notEditableReason;
        private List<PacketField> children;

        public Builder name(String v)               { this.name = v; return this; }
        public Builder path(String v)               { this.path = v; return this; }
        public Builder type(FieldType v)            { this.type = v; return this; }
        public Builder value(Object v)              { this.value = v; return this; }
        public Builder editable(boolean v)          { this.editable = v; return this; }

        public Builder notEditable(String reason) {
            this.editable = false;
            this.notEditableReason = reason;
            return this;
        }

        public Builder children(List<PacketField> v) { this.children = v; return this; }

        public Builder addChild(PacketField c) {
            if (children == null) {
                children = new ArrayList<>();
            }
            children.add(c);
            return this;
        }

        public PacketField build() {
            return new PacketField(this);
        }
    }
}
