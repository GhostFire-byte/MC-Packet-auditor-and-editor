package com.packetauditor;

import com.packetauditor.codec.DecoderService;
import com.packetauditor.match.MatchSpec;
import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.Direction;
import com.packetauditor.model.PacketMeta;
import com.packetauditor.model.ProtocolPhase;
import com.packetauditor.protect.ProtectionPolicy;
import com.packetauditor.rules.EditResult;
import com.packetauditor.rules.EditingRule;
import com.packetauditor.rules.EditingRuleEngine;
import com.packetauditor.rules.FieldReplacement;
import com.packetauditor.rules.ModificationRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditingRuleEngineTest {

    private DecoderService decoderWithAdapter() {
        DecoderService svc = new DecoderService();
        svc.registerAdapter(new TestFixtures.FakeMoveAdapter());
        return svc;
    }

    private CapturedPacket movePacket(double x, double y, double z, boolean onGround) {
        PacketMeta meta = PacketMeta.builder().sequence(1).direction(Direction.OUTBOUND)
                .phase(ProtocolPhase.PLAY).packetName("FakeMovePacket").build();
        return new CapturedPacket(meta, new TestFixtures.FakeMovePacket(x, y, z, onGround), null);
    }

    @Test
    void manualEditBlockedWhenNotAdvanced() {
        ProtectionPolicy protection = new ProtectionPolicy(); // advanced off
        EditingRuleEngine engine = new EditingRuleEngine(decoderWithAdapter(), protection);
        EditResult result = engine.editField(movePacket(1, 2, 3, true), "y", "100");
        assertTrue(result.blocked());
    }

    @Test
    void manualEditAppliesWhenAdvanced() {
        ProtectionPolicy protection = new ProtectionPolicy();
        protection.setAdvancedMode(true);
        EditingRuleEngine engine = new EditingRuleEngine(decoderWithAdapter(), protection);

        List<ModificationRecord> captured = new ArrayList<>();
        engine.setHistorySink(captured::add);

        EditResult result = engine.editField(movePacket(1, 2, 3, true), "y", "100");
        assertTrue(result.applied());
        TestFixtures.FakeMovePacket edited = (TestFixtures.FakeMovePacket) result.resultingNativePacket();
        assertEquals(100.0, edited.y());
        assertEquals(1, captured.size());
        assertEquals("y", captured.get(0).fieldPath());
    }

    @Test
    void ruleBasedEditApplies() {
        ProtectionPolicy protection = new ProtectionPolicy();
        protection.setAdvancedMode(true);
        EditingRuleEngine engine = new EditingRuleEngine(decoderWithAdapter(), protection);

        MatchSpec spec = MatchSpec.builder().packetNameEquals("FakeMovePacket").build();
        EditingRule rule = new EditingRule("force-y-zero", spec);
        rule.addReplacement(new FieldReplacement("y", "0"));
        engine.addRule(rule);

        EditResult result = engine.applyMatchingRules(movePacket(5, 64, 5, true));
        assertTrue(result.applied());
        TestFixtures.FakeMovePacket edited = (TestFixtures.FakeMovePacket) result.resultingNativePacket();
        assertEquals(0.0, edited.y());
    }

    @Test
    void disabledRuleDoesNotApply() {
        ProtectionPolicy protection = new ProtectionPolicy();
        protection.setAdvancedMode(true);
        EditingRuleEngine engine = new EditingRuleEngine(decoderWithAdapter(), protection);

        MatchSpec spec = MatchSpec.builder().packetNameEquals("FakeMovePacket").build();
        EditingRule rule = new EditingRule("disabled", spec);
        rule.addReplacement(new FieldReplacement("y", "0"));
        rule.setEnabled(false);
        engine.addRule(rule);

        EditResult result = engine.applyMatchingRules(movePacket(5, 64, 5, true));
        assertFalse(result.applied());
    }
}
