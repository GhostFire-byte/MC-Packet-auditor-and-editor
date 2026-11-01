package com.packetauditor.mc.net;

import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.Direction;
import com.packetauditor.pipeline.PacketTransport;

import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.TimeUnit;

/**
 * {@link PacketTransport} backed by a Netty {@link ChannelHandlerContext} from
 * the client's connection pipeline (D4).
 *
 * <p>Re-injection semantics:</p>
 * <ul>
 *   <li><b>Inbound</b> delivery re-fires the packet <i>forward</i> from our
 *       handler ({@code ctx.fireChannelRead}) so it reaches the vanilla
 *       {@code packet_handler} as if freshly received.</li>
 *   <li><b>Outbound</b> delivery writes <i>toward the head</i>
 *       ({@code ctx.writeAndFlush}) so it goes to the socket and bypasses our own
 *       {@code write()} (no re-interception loop).</li>
 * </ul>
 *
 * <p>All work is marshalled onto the channel's event loop; the core never blocks
 * it.</p>
 */
public final class NettyPacketTransport implements PacketTransport {

    private final ChannelHandlerContext ctx;

    public NettyPacketTransport(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void deliver(CapturedPacket packet) {
        final Object msg = packet.nativePacket();
        if (msg == null) {
            return;
        }
        Runnable task = () -> {
            if (packet.meta().direction() == Direction.INBOUND) {
                ctx.fireChannelRead(msg);
            } else {
                ctx.writeAndFlush(msg, ctx.voidPromise());
            }
        };
        if (ctx.channel().eventLoop().inEventLoop()) {
            task.run();
        } else {
            ctx.channel().eventLoop().execute(task);
        }
    }

    @Override
    public void execute(Runnable task) {
        if (ctx.channel().eventLoop().inEventLoop()) {
            task.run();
        } else {
            ctx.channel().eventLoop().execute(task);
        }
    }

    @Override
    public void schedule(Runnable task, long delayMillis) {
        ctx.channel().eventLoop().schedule(task, Math.max(0, delayMillis), TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isActive() {
        return ctx.channel().isActive();
    }
}
