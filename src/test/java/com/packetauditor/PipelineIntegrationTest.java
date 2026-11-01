package com.packetauditor;

import com.packetauditor.api.AuditorConfig;
import com.packetauditor.api.PacketAuditor;
import com.packetauditor.match.MatchSpec;
import com.packetauditor.pipeline.CaptureResult;
import com.packetauditor.model.ProtocolPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end pipeline behaviour through the public {@link PacketAuditor} facade,
 * using the fake adapter + transport (D11: no live Minecraft).
 */
class PipelineIntegrationTest {

    private PacketAuditor newAuditor(Path dir) {
        PacketAuditor a = new PacketAuditor(AuditorConfig.builder()
                .baseDir(dir)
                .enableSqlite(false)
                .build());
        a.registerEditAdapter(new TestFixtures.FakeMoveAdapter());
        a.start();
        a.attach(new FakeTransport());
        return a;
    }

    private TestFixtures.FakeMovePacket move() {
        return new TestFixtures.FakeMovePacket(10, 64, 10, true);
    }

    @Test
    void passiveCaptureForwardsByDefault(@TempDir Path dir) {
        PacketAuditor a = newAuditor(dir);
        CaptureResult r = a.handleOutbound(move(), 1, 20, ProtocolPhase.PLAY, null);
        assertEquals(CaptureResult.Action.FORWARD_ORIGINAL, r.action());
        a.stop();
    }

    @Test
    void interceptRuleHoldsPacket(@TempDir Path dir) {
        PacketAuditor a = newAuditor(dir);
        a.protection().setAdvancedMode(true); // interception is a control action (Tier-2 gated)
        a.interceptMatching(MatchSpec.builder().packetNameEquals("FakeMovePacket").build());
        CaptureResult r = a.handleOutbound(move(), 1, 20, ProtocolPhase.PLAY, null);
        assertEquals(CaptureResult.Action.SUPPRESS, r.action());
        assertEquals(1, a.queue().size());
        a.stop();
    }

    @Test
    void discardRuleSuppresses(@TempDir Path dir) {
        PacketAuditor a = newAuditor(dir);
        a.protection().setAdvancedMode(true);
        a.discardMatching(MatchSpec.builder().packetNameEquals("FakeMovePacket").build());
        CaptureResult r = a.handleOutbound(move(), 1, 20, ProtocolPhase.PLAY, null);
        assertEquals(CaptureResult.Action.SUPPRESS, r.action());
        assertEquals(0, a.queue().size());
        a.stop();
    }

    @Test
    void autoEditForwardsReplacement(@TempDir Path dir) {
        PacketAuditor a = newAuditor(dir);
        a.protection().setAdvancedMode(true);
        a.changeAllMatching("FakeMovePacket", "y", "0");
        CaptureResult r = a.handleOutbound(move(), 1, 20, ProtocolPhase.PLAY, null);
        assertEquals(CaptureResult.Action.FORWARD_REPLACEMENT, r.action());
        assertNotNull(r.replacement());
        TestFixtures.FakeMovePacket edited = (TestFixtures.FakeMovePacket) r.replacement();
        assertEquals(0.0, edited.y());
        a.stop();
    }

    @Test
    void simulatedLossDropsStandardPacket(@TempDir Path dir) {
        PacketAuditor a = newAuditor(dir);
        a.protection().setAdvancedMode(true);
        a.netsim().setEnabled(true);
        a.netsim().setOutboundLossProbability(1.0);
        CaptureResult r = a.handleOutbound(move(), 1, 20, ProtocolPhase.PLAY, null);
        assertEquals(CaptureResult.Action.SUPPRESS, r.action());
        a.stop();
    }

    @Test
    void protectedPacketBypassesLossWithoutUnlockAll(@TempDir Path dir) {
        PacketAuditor a = newAuditor(dir);
        a.protection().setAdvancedMode(true);   // advanced, but NOT unlock-all
        a.netsim().setEnabled(true);
        a.netsim().setOutboundLossProbability(1.0);
        CaptureResult r = a.handleOutbound(new TestFixtures.FakeKeepAlivePacket(42), 0, 9,
                ProtocolPhase.PLAY, null);
        // KeepAlive is Tier-1: loss must NOT apply, packet is forwarded.
        assertEquals(CaptureResult.Action.FORWARD_ORIGINAL, r.action());
        a.stop();
    }

    @Test
    void unlockAllAllowsProtectedLoss(@TempDir Path dir) {
        PacketAuditor a = newAuditor(dir);
        a.protection().setUnlockAllProtected(true);
        a.netsim().setEnabled(true);
        a.netsim().setOutboundLossProbability(1.0);
        CaptureResult r = a.handleOutbound(new TestFixtures.FakeKeepAlivePacket(42), 0, 9,
                ProtocolPhase.PLAY, null);
        assertEquals(CaptureResult.Action.SUPPRESS, r.action());
        a.stop();
    }
}
