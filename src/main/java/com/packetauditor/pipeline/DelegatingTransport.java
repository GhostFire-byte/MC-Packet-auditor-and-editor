package com.packetauditor.pipeline;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.util.AuditorLog;

/**
 * A {@link PacketTransport} whose delegate can be swapped at runtime. Lets the
 * queue/delay subsystems be constructed before a channel is attached; when no
 * channel is attached the transport is inactive and delivery is a no-op.
 */
public final class DelegatingTransport implements PacketTransport {

    private static final AuditorLog LOG = AuditorLog.of(DelegatingTransport.class);
    private volatile PacketTransport delegate;

    public void setDelegate(PacketTransport delegate) {
        this.delegate = delegate;
    }

    public PacketTransport delegate() {
        return delegate;
    }

    @Override
    public void deliver(CapturedPacket packet) {
        PacketTransport d = delegate;
        if (d != null) {
            d.deliver(packet);
        } else {
            LOG.debug("No transport attached; dropping delivery of " + packet.meta().packetName());
        }
    }

    @Override
    public void execute(Runnable task) {
        PacketTransport d = delegate;
        if (d != null) {
            d.execute(task);
        } else {
            task.run();
        }
    }

    @Override
    public void schedule(Runnable task, long delayMillis) {
        PacketTransport d = delegate;
        if (d != null) {
            d.schedule(task, delayMillis);
        } else {
            LOG.debug("No transport attached; cannot schedule delayed delivery.");
        }
    }

    @Override
    public boolean isActive() {
        PacketTransport d = delegate;
        return d != null && d.isActive();
    }
}
