package com.packetauditor;

import com.packetauditor.delay.DelayManager;
import com.packetauditor.delay.DelayProfile;
import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.Direction;
import com.packetauditor.model.PacketMeta;
import com.packetauditor.model.ProtocolPhase;
import com.packetauditor.netsim.NetworkSimulator;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayAndNetSimTest {

    private CapturedPacket packet(Direction dir, int size) {
        PacketMeta meta = PacketMeta.builder().sequence(1).direction(dir)
                .phase(ProtocolPhase.PLAY).packetName("P").sizeBytes(size).build();
        return new CapturedPacket(meta, new Object(), null);
    }

    @Test
    void fixedDelayComputesConstant() {
        DelayProfile p = new DelayProfile("fixed").setMode(DelayProfile.Mode.FIXED).setFixedMillis(120);
        assertEquals(120, p.computeDelayMillis(new Random()));
    }

    @Test
    void profileCapClampsDelay() {
        DelayProfile p = new DelayProfile("fixed").setMode(DelayProfile.Mode.FIXED)
                .setFixedMillis(10_000).setMaxDelayMillisCap(2_000);
        assertEquals(2_000, p.computeDelayMillis(new Random()));
    }

    @Test
    void managerDisabledReturnsZero() {
        DelayManager m = new DelayManager();
        DelayProfile p = new DelayProfile("x").setEnabled(true).setFixedMillis(50);
        m.addProfile(p);
        // manager master switch off
        assertEquals(0, m.computeDelayMillis(packet(Direction.OUTBOUND, 10), null));
    }

    @Test
    void managerAppliesMatchingProfile() {
        DelayManager m = new DelayManager();
        m.setEnabled(true);
        m.addProfile(new DelayProfile("x").setEnabled(true).setMode(DelayProfile.Mode.FIXED)
                .setFixedMillis(75).setAppliesToOutbound(true).setAppliesToInbound(false));
        assertEquals(75, m.computeDelayMillis(packet(Direction.OUTBOUND, 10), null));
        assertEquals(0, m.computeDelayMillis(packet(Direction.INBOUND, 10), null));
    }

    @Test
    void netsimDisabledNeverDropsOrThrottles() {
        NetworkSimulator sim = new NetworkSimulator();
        sim.setInboundLossProbability(1.0);
        sim.setInboundBytesPerSecond(1);
        // enabled == false
        assertEquals(false, sim.shouldDrop(Direction.INBOUND));
        assertEquals(0, sim.throttleDelayMillis(Direction.INBOUND, 100000));
    }

    @Test
    void netsimFullLossAlwaysDropsWhenEnabled() {
        NetworkSimulator sim = new NetworkSimulator();
        sim.setEnabled(true);
        sim.setOutboundLossProbability(1.0);
        assertTrue(sim.shouldDrop(Direction.OUTBOUND));
        assertEquals(1, sim.stats().totalDropped());
    }

    @Test
    void netsimThrottleProducesDelayForSecondPacket() {
        NetworkSimulator sim = new NetworkSimulator();
        sim.setEnabled(true);
        sim.setOutboundBytesPerSecond(1000); // 1000 bytes/sec
        // First packet of 1000 bytes: no wait (budget available)
        long first = sim.throttleDelayMillis(Direction.OUTBOUND, 1000);
        // Second immediately after must wait ~1000ms (clamped by maxThrottleDelay default 2000)
        long second = sim.throttleDelayMillis(Direction.OUTBOUND, 1000);
        assertEquals(0, first);
        assertTrue(second > 0);
    }
}
