package com.packetauditor.mc.net;

import com.packetauditor.api.PacketAuditor;
import com.packetauditor.mc.codec.StreamCodecBridge;
import com.packetauditor.model.ProtocolPhase;
import com.packetauditor.pipeline.CaptureResult;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import net.minecraft.network.protocol.Packet;

/**
 * The Netty duplex handler injected around the vanilla {@code packet_handler}
 * (D1). It feeds every decoded inbound/outbound {@link Packet} to the core and
 * honours the returned {@link CaptureResult}: forward unchanged, forward an
 * edited replacement, or suppress (held/delayed/dropped by the core).
 *
 * <p>Non-packet messages (raw {@code ByteBuf}, bundle markers, etc.) pass through
 * untouched, so the handler must sit <i>after</i> the decoder and before the
 * vanilla logic — see {@link PacketAuditorInjector} for placement.</p>
 */
public final class AuditorChannelHandler extends ChannelDuplexHandler {

    public static final String HANDLER_NAME = "packet_auditor";

    private final PacketAuditor auditor;
    private final StreamCodecBridge codec; // nullable
    private volatile ProtocolPhase phase = ProtocolPhase.PLAY;

    public AuditorChannelHandler(PacketAuditor auditor, StreamCodecBridge codec) {
        this.auditor = auditor;
        this.codec = codec;
    }

    /** Update the tracked protocol phase (called by the injector on phase changes). */
    public void setPhase(ProtocolPhase phase) {
        this.phase = phase == null ? ProtocolPhase.UNKNOWN : phase;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        auditor.attach(new NettyPacketTransport(ctx));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        auditor.detach();
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof Packet<?> packet)) {
            ctx.fireChannelRead(msg);
            return;
        }
        CaptureResult result;
        try {
            int id = codec != null ? codec.packetId(packet, com.packetauditor.model.Direction.INBOUND, phase) : -1;
            int size = codec != null ? codec.encodedSize(packet, com.packetauditor.model.Direction.INBOUND, phase) : -1;
            result = auditor.handleInbound(packet, id, size, phase, null);
        } catch (Throwable t) {
            // Fail-safe: never let the module break inbound packet handling.
            ctx.fireChannelRead(packet);
            return;
        }
        switch (result.action()) {
            case FORWARD_ORIGINAL:
                ctx.fireChannelRead(packet);
                break;
            case FORWARD_REPLACEMENT:
                ctx.fireChannelRead(result.replacement());
                break;
            case SUPPRESS:
            default:
                // Held/delayed/dropped by the core; it will re-inject later if needed.
                break;
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof Packet<?> packet)) {
            ctx.write(msg, promise);
            return;
        }
        CaptureResult result;
        try {
            int id = codec != null ? codec.packetId(packet, com.packetauditor.model.Direction.OUTBOUND, phase) : -1;
            int size = codec != null ? codec.encodedSize(packet, com.packetauditor.model.Direction.OUTBOUND, phase) : -1;
            result = auditor.handleOutbound(packet, id, size, phase, null);
        } catch (Throwable t) {
            // Fail-safe: never let the module break outbound sends.
            ctx.write(packet, promise);
            return;
        }
        switch (result.action()) {
            case FORWARD_ORIGINAL:
                ctx.write(packet, promise);
                break;
            case FORWARD_REPLACEMENT:
                ctx.write(result.replacement(), promise);
                break;
            case SUPPRESS:
            default:
                // Not sent now; complete the original promise so MC's send bookkeeping
                // is not left dangling. Released/delayed copies are written later with
                // their own (void) promise via the transport.
                promise.trySuccess();
                break;
        }
    }
}
