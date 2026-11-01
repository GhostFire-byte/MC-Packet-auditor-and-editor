package com.packetauditor;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.pipeline.PacketTransport;

import java.util.ArrayList;
import java.util.List;

/**
 * Synchronous in-memory {@link PacketTransport} for tests: records delivered
 * packets and runs scheduled tasks immediately (D4 re-injection path, simplified).
 */
public final class FakeTransport implements PacketTransport {

    public final List<CapturedPacket> delivered = new ArrayList<>();
    public int scheduledCount = 0;
    private boolean active = true;

    @Override
    public void deliver(CapturedPacket packet) {
        delivered.add(packet);
    }

    @Override
    public void execute(Runnable task) {
        task.run();
    }

    @Override
    public void schedule(Runnable task, long delayMillis) {
        scheduledCount++;
        task.run(); // run immediately in tests
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
