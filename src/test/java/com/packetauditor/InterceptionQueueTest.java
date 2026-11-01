package com.packetauditor;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.Direction;
import com.packetauditor.model.PacketMeta;
import com.packetauditor.model.ProtocolPhase;
import com.packetauditor.queue.InterceptionQueue;
import com.packetauditor.queue.OverflowPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterceptionQueueTest {

    private FakeTransport transport;
    private InterceptionQueue queue;

    @BeforeEach
    void setup() {
        transport = new FakeTransport();
        queue = new InterceptionQueue(transport);
        queue.setDefaultTimeoutMillis(0); // disable auto-release timeout for deterministic tests
    }

    @AfterEach
    void teardown() {
        queue.shutdown();
    }

    private CapturedPacket packet(long seq) {
        PacketMeta meta = PacketMeta.builder().sequence(seq).direction(Direction.OUTBOUND)
                .phase(ProtocolPhase.PLAY).packetName("P" + seq).build();
        return new CapturedPacket(meta, new Object(), null);
    }

    @Test
    void holdThenReleaseDeliversInOrder() {
        long a = queue.hold(packet(1), "t", 0);
        long b = queue.hold(packet(2), "t", 0);
        assertEquals(2, queue.size());
        queue.releaseAll();
        assertEquals(0, queue.size());
        assertEquals(2, transport.delivered.size());
        assertEquals(1, transport.delivered.get(0).meta().sequence());
        assertEquals(2, transport.delivered.get(1).meta().sequence());
        assertTrue(a < b);
    }

    @Test
    void discardDoesNotDeliver() {
        long id = queue.hold(packet(1), "t", 0);
        assertTrue(queue.discard(id));
        assertEquals(0, transport.delivered.size());
        assertEquals(1, queue.status().totalDiscarded());
    }

    @Test
    void releaseEditedDeliversEditedPacket() {
        long id = queue.hold(packet(1), "t", 0);
        CapturedPacket edited = packet(99);
        assertTrue(queue.releaseEdited(id, edited));
        assertEquals(1, transport.delivered.size());
        assertEquals(99, transport.delivered.get(0).meta().sequence());
    }

    @Test
    void overflowReleaseOldest() {
        queue.setMaxSize(2);
        queue.setOverflowPolicy(OverflowPolicy.RELEASE_OLDEST);
        queue.hold(packet(1), "t", 0);
        queue.hold(packet(2), "t", 0);
        queue.hold(packet(3), "t", 0); // should release #1
        assertEquals(2, queue.size());
        assertEquals(1, transport.delivered.size());
        assertEquals(1, transport.delivered.get(0).meta().sequence());
    }

    @Test
    void overflowForwardNewest() {
        queue.setMaxSize(1);
        queue.setOverflowPolicy(OverflowPolicy.FORWARD_NEWEST);
        queue.hold(packet(1), "t", 0);
        long second = queue.hold(packet(2), "t", 0); // forwarded immediately, not held
        assertEquals(-1, second);
        assertEquals(1, queue.size());
        assertEquals(1, transport.delivered.size());
        assertEquals(2, transport.delivered.get(0).meta().sequence());
    }

    @Test
    void unknownIdReleaseIsFalse() {
        assertFalse(queue.release(12345));
    }
}
