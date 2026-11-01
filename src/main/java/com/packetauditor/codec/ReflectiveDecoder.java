package com.packetauditor.codec;

import com.packetauditor.model.DecodedPacket;
import com.packetauditor.model.FieldType;
import com.packetauditor.model.PacketField;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generic, Minecraft-free decoder that builds a read-only structured field tree
 * from any object via reflection. Because Minecraft 26.2 is deobfuscated, record
 * component and field names are the real, human-readable names, so this yields a
 * genuinely useful inspection view for the long tail of packet types that have
 * no curated adapter (D7).
 *
 * <p>The decode is defensive: unreadable fields are marked UNKNOWN rather than
 * throwing, recursion is depth-limited, and reference cycles are detected. The
 * result is always inspect-only — editing requires a curated adapter.</p>
 */
public final class ReflectiveDecoder {

    private static final int MAX_DEPTH = 6;
    private static final int MAX_LIST_ELEMENTS = 64;

    public DecodedPacket decode(Object packet) {
        if (packet == null) {
            return DecodedPacket.rawOnly("No native packet object available.");
        }
        List<PacketField> fields = new ArrayList<>();
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        seen.put(packet, Boolean.TRUE);
        try {
            if (packet.getClass().isRecord()) {
                decodeRecord(packet, "", fields, seen, 0);
            } else {
                decodeFields(packet, "", fields, seen, 0);
            }
        } catch (Throwable t) {
            // Never let reflection blow up the pipeline.
            return DecodedPacket.rawOnly("Reflective decode failed: " + t.getClass().getSimpleName());
        }
        if (fields.isEmpty()) {
            return DecodedPacket.rawOnly("No readable fields found for "
                    + packet.getClass().getSimpleName() + ".");
        }
        return DecodedPacket.reflective(fields);
    }

    private void decodeRecord(Object record, String prefix, List<PacketField> out,
                              Map<Object, Boolean> seen, int depth) {
        RecordComponent[] comps = record.getClass().getRecordComponents();
        for (RecordComponent rc : comps) {
            String name = rc.getName();
            Object value;
            try {
                rc.getAccessor().setAccessible(true);
                value = rc.getAccessor().invoke(record);
            } catch (Throwable t) {
                out.add(PacketField.builder(name)
                        .path(join(prefix, name))
                        .type(FieldType.UNKNOWN)
                        .notEditable("Not readable via reflection")
                        .build());
                continue;
            }
            out.add(toField(name, join(prefix, name), value, seen, depth));
        }
    }

    private void decodeFields(Object obj, String prefix, List<PacketField> out,
                              Map<Object, Boolean> seen, int depth) {
        Class<?> c = obj.getClass();
        // Walk up the class hierarchy to capture inherited fields too.
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                String name = f.getName();
                Object value;
                try {
                    f.setAccessible(true);
                    value = f.get(obj);
                } catch (Throwable t) {
                    out.add(PacketField.builder(name)
                            .path(join(prefix, name))
                            .type(FieldType.UNKNOWN)
                            .notEditable("Not accessible via reflection")
                            .build());
                    continue;
                }
                out.add(toField(name, join(prefix, name), value, seen, depth));
            }
            c = c.getSuperclass();
        }
    }

    private PacketField toField(String name, String path, Object value,
                                Map<Object, Boolean> seen, int depth) {
        if (value == null) {
            return PacketField.builder(name).path(path).type(FieldType.UNKNOWN)
                    .value(null).notEditable("null value").build();
        }

        FieldType type = classify(value);

        switch (type) {
            case COMPOUND: {
                if (depth >= MAX_DEPTH || seen.containsKey(value)) {
                    return PacketField.builder(name).path(path).type(FieldType.COMPOUND)
                            .value(summarize(value))
                            .notEditable(seen.containsKey(value) ? "cycle" : "max depth reached")
                            .build();
                }
                seen.put(value, Boolean.TRUE);
                PacketField.Builder b = PacketField.builder(name).path(path).type(FieldType.COMPOUND);
                List<PacketField> children = new ArrayList<>();
                if (value.getClass().isRecord()) {
                    decodeRecord(value, path, children, seen, depth + 1);
                } else {
                    decodeFields(value, path, children, seen, depth + 1);
                }
                b.children(children).notEditable("Nested structure (inspect-only)");
                return b.build();
            }
            case LIST: {
                PacketField.Builder b = PacketField.builder(name).path(path).type(FieldType.LIST);
                List<PacketField> children = new ArrayList<>();
                int i = 0;
                for (Object element : iterate(value)) {
                    if (i >= MAX_LIST_ELEMENTS) {
                        children.add(PacketField.scalar("\u2026", FieldType.STRING,
                                "(" + (size(value) - MAX_LIST_ELEMENTS) + " more)", false));
                        break;
                    }
                    children.add(toField("[" + i + "]", path + "[" + i + "]", element,
                            seen, depth + 1));
                    i++;
                }
                b.children(children).notEditable("List (inspect-only)");
                return b.build();
            }
            case BYTES: {
                int len = Array.getLength(value);
                return PacketField.builder(name).path(path).type(FieldType.BYTES)
                        .value("byte[" + len + "]")
                        .notEditable("Raw bytes (use the raw editor)")
                        .build();
            }
            default:
                // Scalars from reflective decode are inspect-only (no curated adapter).
                return PacketField.builder(name).path(path).type(type).value(value)
                        .notEditable("Reflective decode (inspect-only)")
                        .build();
        }
    }

    private FieldType classify(Object value) {
        if (value instanceof Boolean)   return FieldType.BOOLEAN;
        if (value instanceof Byte)      return FieldType.BYTE;
        if (value instanceof Short)     return FieldType.SHORT;
        if (value instanceof Integer)   return FieldType.INT;
        if (value instanceof Long)      return FieldType.LONG;
        if (value instanceof Float)     return FieldType.FLOAT;
        if (value instanceof Double)    return FieldType.DOUBLE;
        if (value instanceof Character) return FieldType.STRING;
        if (value instanceof CharSequence) return FieldType.STRING;
        if (value instanceof UUID)      return FieldType.UUID;
        if (value instanceof Enum<?>)   return FieldType.ENUM;
        if (value instanceof byte[])    return FieldType.BYTES;
        if (value.getClass().isArray()) return FieldType.LIST;
        if (value instanceof Collection<?>) return FieldType.LIST;
        if (value instanceof Number)    return FieldType.DOUBLE;
        return FieldType.COMPOUND;
    }

    private Iterable<Object> iterate(Object value) {
        List<Object> list = new ArrayList<>();
        if (value instanceof Collection<?> c) {
            list.addAll(c);
        } else if (value.getClass().isArray()) {
            int n = Array.getLength(value);
            for (int i = 0; i < n; i++) {
                list.add(Array.get(value, i));
            }
        }
        return list;
    }

    private int size(Object value) {
        if (value instanceof Collection<?> c) {
            return c.size();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return 0;
    }

    private String summarize(Object value) {
        return value.getClass().getSimpleName();
    }

    private static String join(String prefix, String name) {
        return prefix == null || prefix.isEmpty() ? name : prefix + "." + name;
    }
}
