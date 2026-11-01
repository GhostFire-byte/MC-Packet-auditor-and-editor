package com.packetauditor.mc.net;

import com.packetauditor.api.PacketAuditor;
import com.packetauditor.mc.codec.StreamCodecBridge;
import com.packetauditor.util.AuditorLog;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

import net.minecraft.network.Connection;

import java.lang.reflect.Field;

/**
 * Injects the {@link AuditorChannelHandler} into a client {@link Connection}'s
 * Netty pipeline, just before the vanilla {@code packet_handler} so it sees fully
 * decoded packets in both directions (D1/D2).
 *
 * <p>Because Minecraft 26.2 is deobfuscated, the {@code Connection}'s channel is
 * accessed by its real name; a reflective fallback scans for the first
 * {@link Channel}-typed field in case the field name differs in your build.</p>
 */
public final class PacketAuditorInjector {

    private static final AuditorLog LOG = AuditorLog.of(PacketAuditorInjector.class);

    private PacketAuditorInjector() {
    }

    /**
     * Inject the handler into the given connection.
     *
     * @return true if the handler was inserted.
     */
    public static boolean inject(Connection connection, PacketAuditor auditor, StreamCodecBridge codec) {
        Channel channel = resolveChannel(connection);
        if (channel == null) {
            LOG.warn("Could not resolve Netty channel from Connection; injection skipped.");
            return false;
        }
        return injectIntoChannel(channel, auditor, codec);
    }

    /** Inject directly into an already-resolved channel. */
    public static boolean injectIntoChannel(Channel channel, PacketAuditor auditor,
                                            StreamCodecBridge codec) {
        if (channel == null || !channel.isOpen()) {
            return false;
        }
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(AuditorChannelHandler.HANDLER_NAME) != null) {
            return true; // already injected
        }
        AuditorChannelHandler handler = new AuditorChannelHandler(auditor, codec);
        Runnable add = () -> {
            if (pipeline.get("packet_handler") != null) {
                pipeline.addBefore("packet_handler", AuditorChannelHandler.HANDLER_NAME, handler);
            } else {
                // Fall back to the tail if the vanilla handler name differs.
                pipeline.addLast(AuditorChannelHandler.HANDLER_NAME, handler);
            }
        };
        if (channel.eventLoop().inEventLoop()) {
            add.run();
        } else {
            channel.eventLoop().execute(add);
        }
        LOG.info("Packet Auditor handler injected into channel pipeline.");
        return true;
    }

    /** Remove the handler from a channel (e.g. on toggle-off). */
    public static void remove(Channel channel) {
        if (channel == null) {
            return;
        }
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(AuditorChannelHandler.HANDLER_NAME) != null) {
            channel.eventLoop().execute(() -> {
                if (pipeline.get(AuditorChannelHandler.HANDLER_NAME) != null) {
                    pipeline.remove(AuditorChannelHandler.HANDLER_NAME);
                }
            });
        }
    }

    private static Channel resolveChannel(Connection connection) {
        if (connection == null) {
            return null;
        }
        // Reflective scan is robust across field-name changes; 26.2 names are stable
        // but this keeps the adapter resilient.
        Class<?> c = connection.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object value = f.get(connection);
                        if (value instanceof Channel ch) {
                            return ch;
                        }
                    } catch (Throwable ignored) {
                        // keep scanning
                    }
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }
}
