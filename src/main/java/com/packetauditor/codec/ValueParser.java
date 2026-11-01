package com.packetauditor.codec;

import com.packetauditor.model.FieldType;

import java.util.UUID;

/**
 * Parses user-entered text into a typed value appropriate for a decoded field,
 * and reports clear validation errors. Used by the structured editor and the
 * editing-rule engine before an edit is handed to a {@link PacketEditAdapter}.
 */
public final class ValueParser {

    private ValueParser() {
    }

    /** Thrown when text cannot be parsed to the requested field type. */
    public static final class ParseException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        public ParseException(String message) {
            super(message);
        }
    }

    /**
     * Parse {@code text} into a value compatible with {@code type}.
     *
     * @throws ParseException with a user-facing message if the text is invalid
     */
    public static Object parse(FieldType type, String text) {
        String t = text == null ? "" : text.trim();
        try {
            switch (type) {
                case BOOLEAN:
                    if (t.equalsIgnoreCase("true"))  return Boolean.TRUE;
                    if (t.equalsIgnoreCase("false")) return Boolean.FALSE;
                    throw new ParseException("Expected true or false");
                case BYTE:    return Byte.parseByte(t);
                case SHORT:   return Short.parseShort(t);
                case INT:
                case VARINT:  return Integer.parseInt(t);
                case LONG:
                case VARLONG: return Long.parseLong(t);
                case FLOAT:   return Float.parseFloat(t);
                case DOUBLE:  return Double.parseDouble(t);
                case UUID:    return UUID.fromString(t);
                case STRING:
                case IDENTIFIER:
                case ENUM:
                    return t;
                default:
                    return t;
            }
        } catch (NumberFormatException e) {
            throw new ParseException("\"" + text + "\" is not a valid " + type + " value");
        } catch (IllegalArgumentException e) {
            throw new ParseException(e.getMessage() == null
                    ? "Invalid " + type + " value" : e.getMessage());
        }
    }

    /**
     * Validate text for a field type without returning the value.
     *
     * @return null if valid, else a user-facing error message
     */
    public static String validate(FieldType type, String text) {
        try {
            parse(type, text);
            return null;
        } catch (ParseException e) {
            return e.getMessage();
        }
    }
}
