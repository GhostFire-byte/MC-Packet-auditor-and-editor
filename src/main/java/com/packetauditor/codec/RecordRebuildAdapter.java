package com.packetauditor.codec;

import com.packetauditor.model.DecodedPacket;
import com.packetauditor.model.FieldType;
import com.packetauditor.model.PacketField;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A curated {@link PacketEditAdapter} that works for <i>any</i> immutable record
 * packet by reading its components reflectively and rebuilding a new instance via
 * the canonical constructor with one component replaced (D7). Because Minecraft
 * 26.2 is deobfuscated, component names are the real field names, so this gives
 * genuine editable support for a broad set of record packets without
 * hand-writing an adapter per type.
 *
 * <p>Register one per packet class (optionally restricting which fields are
 * editable) from the MC integration.</p>
 */
public final class RecordRebuildAdapter implements PacketEditAdapter {

    private final Class<?> type;
    private final String label;
    private final Set<String> editableFields; // empty == all scalar fields editable

    public RecordRebuildAdapter(Class<?> type, String label, Set<String> editableFields) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException(type.getName() + " is not a record");
        }
        this.type = type;
        this.label = label == null ? type.getSimpleName() : label;
        this.editableFields = editableFields == null ? Set.of() : Set.copyOf(editableFields);
    }

    public static RecordRebuildAdapter forClass(Class<?> type, String... editableFields) {
        return new RecordRebuildAdapter(type, type.getSimpleName(),
                editableFields == null ? Set.of() : Set.of(editableFields));
    }

    @Override
    public boolean supports(Object nativePacket) {
        return nativePacket != null && nativePacket.getClass() == type;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public DecodedPacket decode(Object nativePacket) {
        RecordComponent[] comps = nativePacket.getClass().getRecordComponents();
        List<PacketField> fields = new ArrayList<>(comps.length);
        boolean anyEditable = false;
        for (RecordComponent rc : comps) {
            Object value = readComponent(nativePacket, rc);
            FieldType ft = classify(rc.getType(), value);
            boolean editable = isEditableField(rc.getName()) && isScalarEditable(ft);
            if (editable) {
                anyEditable = true;
                fields.add(PacketField.scalar(rc.getName(), ft, value, true));
            } else {
                fields.add(PacketField.builder(rc.getName()).type(ft).value(displayOf(value))
                        .notEditable(isScalarEditable(ft) ? "field not marked editable"
                                : "complex field (inspect-only)")
                        .build());
            }
        }
        return DecodedPacket.curated(fields, anyEditable,
                anyEditable ? null : "No editable scalar fields for " + type.getSimpleName());
    }

    @Override
    public Object applyEdit(Object nativePacket, String fieldPath, Object newValue)
            throws EditException {
        RecordComponent[] comps = nativePacket.getClass().getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[comps.length];
        Object[] args = new Object[comps.length];
        boolean matched = false;
        for (int i = 0; i < comps.length; i++) {
            RecordComponent rc = comps[i];
            paramTypes[i] = rc.getType();
            if (rc.getName().equals(fieldPath)) {
                if (!isEditableField(rc.getName())) {
                    throw new EditException("Field '" + fieldPath + "' is not editable");
                }
                args[i] = coerce(rc.getType(), newValue);
                matched = true;
            } else {
                args[i] = readComponent(nativePacket, rc);
            }
        }
        if (!matched) {
            throw new EditException("No such field: " + fieldPath);
        }
        try {
            Constructor<?> ctor = type.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (Throwable t) {
            throw new EditException("Could not rebuild " + type.getSimpleName() + ": " + t.getMessage(), t);
        }
    }

    private boolean isEditableField(String name) {
        return editableFields.isEmpty() || editableFields.contains(name);
    }

    private static Object readComponent(Object obj, RecordComponent rc) {
        try {
            rc.getAccessor().setAccessible(true);
            return rc.getAccessor().invoke(obj);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isScalarEditable(FieldType ft) {
        switch (ft) {
            case BOOLEAN: case BYTE: case SHORT: case INT: case LONG:
            case FLOAT: case DOUBLE: case STRING: case UUID:
                return true;
            default:
                return false;
        }
    }

    private static FieldType classify(Class<?> declared, Object value) {
        Class<?> c = value != null ? value.getClass() : declared;
        if (c == boolean.class || c == Boolean.class) return FieldType.BOOLEAN;
        if (c == byte.class || c == Byte.class)       return FieldType.BYTE;
        if (c == short.class || c == Short.class)     return FieldType.SHORT;
        if (c == int.class || c == Integer.class)     return FieldType.INT;
        if (c == long.class || c == Long.class)       return FieldType.LONG;
        if (c == float.class || c == Float.class)     return FieldType.FLOAT;
        if (c == double.class || c == Double.class)   return FieldType.DOUBLE;
        if (CharSequence.class.isAssignableFrom(c))   return FieldType.STRING;
        if (c == UUID.class)                          return FieldType.UUID;
        if (c.isEnum())                               return FieldType.ENUM;
        return FieldType.COMPOUND;
    }

    private static Object coerce(Class<?> target, Object value) {
        if (value == null) {
            return null;
        }
        if (target == String.class) {
            return value.toString();
        }
        if ((target == boolean.class || target == Boolean.class)) {
            return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
        }
        if (value instanceof Number n) {
            if (target == byte.class || target == Byte.class)     return n.byteValue();
            if (target == short.class || target == Short.class)   return n.shortValue();
            if (target == int.class || target == Integer.class)   return n.intValue();
            if (target == long.class || target == Long.class)     return n.longValue();
            if (target == float.class || target == Float.class)   return n.floatValue();
            if (target == double.class || target == Double.class) return n.doubleValue();
        }
        if (target == UUID.class) {
            return value instanceof UUID ? value : UUID.fromString(value.toString());
        }
        return value;
    }

    private static Object displayOf(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number || value instanceof Boolean || value instanceof CharSequence
                ? value : value.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return "RecordRebuildAdapter[" + type.getSimpleName() + "]";
    }
}
