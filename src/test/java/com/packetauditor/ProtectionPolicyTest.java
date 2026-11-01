package com.packetauditor;

import com.packetauditor.model.Direction;
import com.packetauditor.model.PacketMeta;
import com.packetauditor.model.ProtocolPhase;
import com.packetauditor.protect.OperationType;
import com.packetauditor.protect.ProtectionPolicy;
import com.packetauditor.protect.ProtectionTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionPolicyTest {

    private PacketMeta meta(String name, ProtocolPhase phase) {
        return PacketMeta.builder().sequence(1).direction(Direction.OUTBOUND)
                .phase(phase).packetName(name).packetClass("net.minecraft.network." + name).build();
    }

    @Test
    void keepAliveIsTier1Essential() {
        ProtectionPolicy p = new ProtectionPolicy();
        assertEquals(ProtectionTier.TIER1_ESSENTIAL,
                p.classify(meta("ServerboundKeepAlivePacket", ProtocolPhase.PLAY)));
    }

    @Test
    void movementIsTier2Standard() {
        ProtectionPolicy p = new ProtectionPolicy();
        assertEquals(ProtectionTier.TIER2_STANDARD,
                p.classify(meta("ServerboundMovePlayerPacket", ProtocolPhase.PLAY)));
    }

    @Test
    void loginPhaseAlwaysEssential() {
        ProtectionPolicy p = new ProtectionPolicy();
        assertEquals(ProtectionTier.TIER1_ESSENTIAL,
                p.classify(meta("SomethingDuringLogin", ProtocolPhase.LOGIN)));
    }

    @Test
    void normalModeBlocksStandardEdits() {
        ProtectionPolicy p = new ProtectionPolicy();
        // Advanced off by default: editing even a standard packet is blocked.
        assertTrue(p.evaluate(meta("ServerboundMovePlayerPacket", ProtocolPhase.PLAY),
                OperationType.EDIT).blocked());
    }

    @Test
    void advancedModeUnlocksStandardButNotEssential() {
        ProtectionPolicy p = new ProtectionPolicy();
        p.setAdvancedMode(true);
        assertTrue(p.evaluate(meta("ServerboundMovePlayerPacket", ProtocolPhase.PLAY),
                OperationType.EDIT).allowed());
        assertTrue(p.evaluate(meta("ServerboundKeepAlivePacket", ProtocolPhase.PLAY),
                OperationType.EDIT).blocked());
    }

    @Test
    void unlockAllUnlocksEssential() {
        ProtectionPolicy p = new ProtectionPolicy();
        p.setUnlockAllProtected(true);
        assertTrue(p.evaluate(meta("ServerboundKeepAlivePacket", ProtocolPhase.PLAY),
                OperationType.SIMULATE_LOSS).allowed());
    }

    @Test
    void inspectAlwaysAllowed() {
        ProtectionPolicy p = new ProtectionPolicy();
        assertTrue(p.evaluate(meta("ServerboundKeepAlivePacket", ProtocolPhase.PLAY),
                OperationType.INSPECT).allowed());
    }

    @Test
    void blockedDecisionCarriesReason() {
        ProtectionPolicy p = new ProtectionPolicy();
        String reason = p.evaluate(meta("ServerboundKeepAlivePacket", ProtocolPhase.PLAY),
                OperationType.EDIT).reason();
        assertFalse(reason.isEmpty());
    }
}
