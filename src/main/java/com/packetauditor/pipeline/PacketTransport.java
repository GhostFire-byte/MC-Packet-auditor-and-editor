package com.packetauditor.pipeline;

import com.packetauditor.model.CapturedPacket;

/**
 * Abstraction over the Minecraft/Netty channel that the core uses to forward,
 * re-inject and schedule packets without compiling against Netty or Minecraft.
 * Implemented in {@code src/mc} by the Netty handler adapter; unit tests provide
 * a synchronous in-memory implementation.
 *
 * <p>Thread-affinity rule (D4): all delivery must ultimately happen on the
 * channel's event loop. {@link #execute(Runnable)} and {@link #schedule(Runnable, long)}
 * hand work to that loop; the core never blocks it.</p>
 */
public interface PacketTransport {

    /**
     * Deliver a packet in its own direction:
     * <ul>
     *   <li>INBOUND — re-fire into the client's normal inbound handling, as if it
     *       had just arrived (its effects land at release time, D4).</li>
     *   <li>OUTBOUND — write/flush it to the server.</li>
     * </ul>
     * Implementations must marshal onto the event loop themselves if needed.
     */
    void deliver(CapturedPacket packet);

    /** Run a task on the channel's event loop as soon as possible. */
    void execute(Runnable task);

    /**
     * Schedule a task on the channel's event loop after {@code delayMillis}
     * (D4: uses the event-loop scheduler — no sleeping threads).
     */
    void schedule(Runnable task, long delayMillis);

    /** True if the transport/channel is currently active (connected). */
    boolean isActive();
}
