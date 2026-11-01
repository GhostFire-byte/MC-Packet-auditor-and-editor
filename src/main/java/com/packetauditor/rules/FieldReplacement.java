package com.packetauditor.rules;

import java.util.Objects;

/**
 * A single field replacement in an editing rule: set {@code fieldPath} to
 * {@code valueText} (parsed to the field's type at apply time).
 */
public final class FieldReplacement {

    private final String fieldPath;
    private final String valueText;

    public FieldReplacement(String fieldPath, String valueText) {
        this.fieldPath = Objects.requireNonNull(fieldPath, "fieldPath");
        this.valueText = valueText == null ? "" : valueText;
    }

    public String fieldPath() {
        return fieldPath;
    }

    public String valueText() {
        return valueText;
    }

    @Override
    public String toString() {
        return fieldPath + " := " + valueText;
    }
}
