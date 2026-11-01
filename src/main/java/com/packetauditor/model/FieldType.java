package com.packetauditor.model;

/**
 * Logical value kind of a decoded field, used by the editor to pick an input
 * widget and by the validator to bound-check edits.
 */
public enum FieldType {
    BOOLEAN,
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    STRING,
    UUID,
    ENUM,
    VARINT,
    VARLONG,
    BYTES,          // raw byte[] / ByteBuf slice
    IDENTIFIER,     // e.g. a resource location "minecraft:stone"
    COMPOUND,       // nested object with children
    LIST,           // repeated children
    UNKNOWN;        // present but not interpretable

    public boolean isNumeric() {
        switch (this) {
            case BYTE: case SHORT: case INT: case LONG:
            case FLOAT: case DOUBLE: case VARINT: case VARLONG:
                return true;
            default:
                return false;
        }
    }

    public boolean isContainer() {
        return this == COMPOUND || this == LIST;
    }
}
