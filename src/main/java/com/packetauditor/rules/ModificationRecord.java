package com.packetauditor.rules;

import com.packetauditor.model.Direction;

/**
 * An immutable record of one field modification (spec §5 "editing history"):
 * which rule (if any) applied it, the packet, the field, and the before/after
 * values, with a timestamp.
 */
public final class ModificationRecord {

    private final long timestampMillis;
    private final String ruleId;         // null for manual edits
    private final String ruleName;       // "(manual)" for manual edits
    private final String packetName;
    private final Direction direction;
    private final String fieldPath;
    private final String originalValue;
    private final String modifiedValue;

    public ModificationRecord(long timestampMillis, String ruleId, String ruleName,
                              String packetName, Direction direction, String fieldPath,
                              String originalValue, String modifiedValue) {
        this.timestampMillis = timestampMillis;
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.packetName = packetName;
        this.direction = direction;
        this.fieldPath = fieldPath;
        this.originalValue = originalValue;
        this.modifiedValue = modifiedValue;
    }

    public long timestampMillis() { return timestampMillis; }
    public String ruleId()        { return ruleId; }
    public String ruleName()      { return ruleName; }
    public String packetName()    { return packetName; }
    public Direction direction()  { return direction; }
    public String fieldPath()     { return fieldPath; }
    public String originalValue() { return originalValue; }
    public String modifiedValue() { return modifiedValue; }

    public boolean isManual() {
        return ruleId == null;
    }

    @Override
    public String toString() {
        return "[" + (isManual() ? "manual" : ruleName) + "] " + packetName + "." + fieldPath
                + ": " + originalValue + " -> " + modifiedValue;
    }
}
