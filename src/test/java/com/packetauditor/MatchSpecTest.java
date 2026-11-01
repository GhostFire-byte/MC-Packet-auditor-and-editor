package com.packetauditor;

import com.packetauditor.match.ConditionOperator;
import com.packetauditor.match.FieldCondition;
import com.packetauditor.match.LogicOp;
import com.packetauditor.match.MatchMode;
import com.packetauditor.match.MatchSpec;
import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.Direction;
import com.packetauditor.model.PacketMeta;
import com.packetauditor.model.ProtocolPhase;
import com.packetauditor.codec.ReflectiveDecoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchSpecTest {

    private CapturedPacket packet(String name, int size) {
        PacketMeta meta = PacketMeta.builder().sequence(1).direction(Direction.OUTBOUND)
                .phase(ProtocolPhase.PLAY).packetName(name).sizeBytes(size).build();
        return new CapturedPacket(meta, new TestFixtures.FakeMovePacket(1, 2, 3, true), null);
    }

    @Test
    void basicMatchByType() {
        MatchSpec spec = MatchSpec.builder().packetNameEquals("FakeMovePacket").build();
        assertEquals(MatchMode.BASIC, spec.requiredMode());
        assertTrue(spec.matches(packet("FakeMovePacket", 10), null));
        assertFalse(spec.matches(packet("OtherPacket", 10), null));
    }

    @Test
    void fieldConditionRequiresIntermediate() {
        MatchSpec spec = MatchSpec.builder()
                .addCondition(new FieldCondition("y", ConditionOperator.LESS_THAN, "0"))
                .build();
        assertEquals(MatchMode.INTERMEDIATE, spec.requiredMode());
    }

    @Test
    void orOrSizeRequiresAdvanced() {
        MatchSpec orSpec = MatchSpec.builder().logic(LogicOp.OR)
                .addCondition(new FieldCondition("y", ConditionOperator.LESS_THAN, "0")).build();
        assertEquals(MatchMode.ADVANCED, orSpec.requiredMode());

        MatchSpec sizeSpec = MatchSpec.builder().minSize(100).build();
        assertEquals(MatchMode.ADVANCED, sizeSpec.requiredMode());
    }

    @Test
    void invalidUnderLowerMode() {
        MatchSpec spec = MatchSpec.builder().minSize(100).build();
        assertFalse(spec.isValidUnder(MatchMode.BASIC));
        assertTrue(spec.isValidUnder(MatchMode.ADVANCED));
    }

    @Test
    void fieldConditionEvaluatesAgainstDecoded() {
        CapturedPacket p = packet("FakeMovePacket", 10);
        var decoded = new ReflectiveDecoder().decode(p.nativePacket());
        // y == 2.0, so "y < 0" is false, "y >= 2" is true
        assertFalse(new FieldCondition("y", ConditionOperator.LESS_THAN, "0").evaluate(decoded));
        assertTrue(new FieldCondition("y", ConditionOperator.GREATER_OR_EQUAL, "2").evaluate(decoded));
        assertTrue(new FieldCondition("onGround", ConditionOperator.EQUALS, "true").evaluate(decoded));
    }

    @Test
    void andLogicRequiresAll() {
        CapturedPacket p = packet("FakeMovePacket", 10);
        var decoded = new ReflectiveDecoder().decode(p.nativePacket());
        MatchSpec spec = MatchSpec.builder()
                .addCondition(new FieldCondition("onGround", ConditionOperator.EQUALS, "true"))
                .addCondition(new FieldCondition("x", ConditionOperator.EQUALS, "1"))
                .build();
        assertTrue(spec.matches(p, decoded));

        MatchSpec failing = MatchSpec.builder()
                .addCondition(new FieldCondition("onGround", ConditionOperator.EQUALS, "false"))
                .addCondition(new FieldCondition("x", ConditionOperator.EQUALS, "1"))
                .build();
        assertFalse(failing.matches(p, decoded));
    }
}
