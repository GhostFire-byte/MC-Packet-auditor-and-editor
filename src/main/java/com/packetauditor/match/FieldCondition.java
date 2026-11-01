package com.packetauditor.match;

import com.packetauditor.model.DecodedPacket;
import com.packetauditor.model.PacketField;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A single condition on a decoded packet field, e.g. {@code position.y < 0}.
 * Conditions are evaluated against a {@link DecodedPacket}; a missing field
 * evaluates to false (except for the {@link ConditionOperator#EXISTS} operator).
 */
public final class FieldCondition {

    private final String fieldPath;
    private final ConditionOperator operator;
    private final String expected;      // raw textual expectation; parsed per field type
    private transient Pattern compiledRegex;

    public FieldCondition(String fieldPath, ConditionOperator operator, String expected) {
        this.fieldPath = Objects.requireNonNull(fieldPath, "fieldPath");
        this.operator = Objects.requireNonNull(operator, "operator");
        this.expected = expected == null ? "" : expected;
    }

    public String fieldPath()           { return fieldPath; }
    public ConditionOperator operator() { return operator; }
    public String expected()            { return expected; }

    public boolean evaluate(DecodedPacket decoded) {
        if (decoded == null) {
            return false;
        }
        Optional<PacketField> field = decoded.field(fieldPath);
        if (operator == ConditionOperator.EXISTS) {
            return field.isPresent();
        }
        if (field.isEmpty()) {
            return false;
        }
        Object actual = field.get().value();
        if (actual == null) {
            return false;
        }
        return compare(actual);
    }

    private boolean compare(Object actual) {
        switch (operator) {
            case EQUALS:
                return matchesEquality(actual);
            case NOT_EQUALS:
                return !matchesEquality(actual);
            case GREATER_THAN:
            case GREATER_OR_EQUAL:
            case LESS_THAN:
            case LESS_OR_EQUAL:
                return compareNumeric(actual);
            case CONTAINS:
                return String.valueOf(actual).contains(expected);
            case STARTS_WITH:
                return String.valueOf(actual).startsWith(expected);
            case ENDS_WITH:
                return String.valueOf(actual).endsWith(expected);
            case MATCHES_REGEX:
                return matchesRegex(String.valueOf(actual));
            default:
                return false;
        }
    }

    private boolean matchesEquality(Object actual) {
        // Numeric-aware equality so "1" matches 1, 1.0, etc.
        Double a = asDouble(actual);
        Double e = parseExpectedDouble();
        if (a != null && e != null) {
            return a.doubleValue() == e.doubleValue();
        }
        if (actual instanceof Boolean) {
            return Boolean.parseBoolean(expected) == (Boolean) actual;
        }
        return String.valueOf(actual).equals(expected);
    }

    private boolean compareNumeric(Object actual) {
        Double a = asDouble(actual);
        Double e = parseExpectedDouble();
        if (a == null || e == null) {
            return false;
        }
        int cmp = Double.compare(a, e);
        switch (operator) {
            case GREATER_THAN:     return cmp > 0;
            case GREATER_OR_EQUAL: return cmp >= 0;
            case LESS_THAN:        return cmp < 0;
            case LESS_OR_EQUAL:    return cmp <= 0;
            default:               return false;
        }
    }

    private boolean matchesRegex(String actual) {
        try {
            if (compiledRegex == null) {
                compiledRegex = Pattern.compile(expected);
            }
            return compiledRegex.matcher(actual).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private Double parseExpectedDouble() {
        try {
            return Double.parseDouble(expected.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof Character c) {
            return (double) (int) c.charValue();
        }
        if (value instanceof CharSequence s) {
            try {
                return Double.parseDouble(s.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return fieldPath + " " + operator.symbol() + " " + expected;
    }
}
