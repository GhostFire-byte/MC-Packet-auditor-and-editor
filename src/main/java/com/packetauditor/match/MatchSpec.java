package com.packetauditor.match;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.DecodedPacket;
import com.packetauditor.model.Direction;
import com.packetauditor.model.PacketMeta;
import com.packetauditor.model.ProtocolPhase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable specification of which packets to match (spec §7). Reused by
 * editing rules, interception rules and delay rules. A spec computes the minimal
 * {@link MatchMode} it requires so the engine can mark it inactive when the
 * current mode is lower.
 */
public final class MatchSpec {

    private final String packetNameEquals;   // nullable, simple class name
    private final String packetNameContains; // nullable
    private final Integer packetId;          // nullable
    private final Direction direction;       // nullable == any
    private final ProtocolPhase phase;       // nullable == any
    private final Integer minSize;           // nullable
    private final Integer maxSize;           // nullable
    private final List<FieldCondition> conditions;
    private final LogicOp logic;

    private MatchSpec(Builder b) {
        this.packetNameEquals = emptyToNull(b.packetNameEquals);
        this.packetNameContains = emptyToNull(b.packetNameContains);
        this.packetId = b.packetId;
        this.direction = b.direction;
        this.phase = b.phase;
        this.minSize = b.minSize;
        this.maxSize = b.maxSize;
        this.conditions = Collections.unmodifiableList(new ArrayList<>(b.conditions));
        this.logic = b.logic == null ? LogicOp.AND : b.logic;
    }

    public String packetNameEquals()       { return packetNameEquals; }
    public String packetNameContains()     { return packetNameContains; }
    public Integer packetId()              { return packetId; }
    public Direction direction()           { return direction; }
    public ProtocolPhase phase()           { return phase; }
    public Integer minSize()               { return minSize; }
    public Integer maxSize()               { return maxSize; }
    public List<FieldCondition> conditions(){ return conditions; }
    public LogicOp logic()                 { return logic; }

    /** The lowest {@link MatchMode} under which this spec is valid. */
    public MatchMode requiredMode() {
        if (logic == LogicOp.OR || minSize != null || maxSize != null) {
            return MatchMode.ADVANCED;
        }
        if (!conditions.isEmpty()) {
            return MatchMode.INTERMEDIATE;
        }
        return MatchMode.BASIC;
    }

    public boolean isValidUnder(MatchMode mode) {
        return mode.satisfies(requiredMode());
    }

    /**
     * Evaluate this spec against a captured packet and its decoded view
     * (decoded may be null if conditions reference fields; those evaluate false).
     */
    public boolean matches(CapturedPacket packet, DecodedPacket decoded) {
        PacketMeta meta = packet.meta();

        if (direction != null && meta.direction() != direction) {
            return false;
        }
        if (phase != null && meta.phase() != phase) {
            return false;
        }
        if (packetId != null && meta.packetId() != packetId) {
            return false;
        }
        if (packetNameEquals != null && !packetNameEquals.equalsIgnoreCase(meta.packetName())) {
            return false;
        }
        if (packetNameContains != null
                && !meta.packetName().toLowerCase().contains(packetNameContains.toLowerCase())) {
            return false;
        }
        if (minSize != null && (meta.sizeBytes() < 0 || meta.sizeBytes() < minSize)) {
            return false;
        }
        if (maxSize != null && (meta.sizeBytes() < 0 || meta.sizeBytes() > maxSize)) {
            return false;
        }

        if (conditions.isEmpty()) {
            return true;
        }
        if (logic == LogicOp.AND) {
            for (FieldCondition c : conditions) {
                if (!c.evaluate(decoded)) {
                    return false;
                }
            }
            return true;
        } else {
            for (FieldCondition c : conditions) {
                if (c.evaluate(decoded)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    public static final class Builder {
        private String packetNameEquals;
        private String packetNameContains;
        private Integer packetId;
        private Direction direction;
        private ProtocolPhase phase;
        private Integer minSize;
        private Integer maxSize;
        private final List<FieldCondition> conditions = new ArrayList<>();
        private LogicOp logic = LogicOp.AND;

        public Builder packetNameEquals(String v)   { this.packetNameEquals = v; return this; }
        public Builder packetNameContains(String v) { this.packetNameContains = v; return this; }
        public Builder packetId(Integer v)          { this.packetId = v; return this; }
        public Builder direction(Direction v)       { this.direction = v; return this; }
        public Builder phase(ProtocolPhase v)       { this.phase = v; return this; }
        public Builder minSize(Integer v)           { this.minSize = v; return this; }
        public Builder maxSize(Integer v)           { this.maxSize = v; return this; }
        public Builder logic(LogicOp v)             { this.logic = v; return this; }

        public Builder addCondition(FieldCondition c) {
            if (c != null) {
                conditions.add(c);
            }
            return this;
        }

        public MatchSpec build() {
            return new MatchSpec(this);
        }
    }
}
