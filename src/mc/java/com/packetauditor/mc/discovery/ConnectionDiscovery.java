package com.packetauditor.mc.discovery;

import com.packetauditor.api.PacketAuditor;
import com.packetauditor.mc.codec.StreamCodecBridge;
import com.packetauditor.mc.net.PacketAuditorInjector;
import com.packetauditor.util.AuditorLog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;

/**
 * Optional self-discovery of the active client connection (D2 hybrid, option C).
 * Polls {@link Minecraft} for a live {@link Connection} and injects the handler
 * automatically, so "just attach the agent" works without an explicit integration
 * call. Safe because 26.2 names are stable; the supported path is still the
 * host-provided {@code attach()} / {@link PacketAuditorInjector}.
 */
public final class ConnectionDiscovery {

    private static final AuditorLog LOG = AuditorLog.of(ConnectionDiscovery.class);

    private final PacketAuditor auditor;
    private final StreamCodecBridge codec;
    private Thread watcher;
    private volatile boolean running;
    private volatile Connection injectedInto;

    public ConnectionDiscovery(PacketAuditor auditor, StreamCodecBridge codec) {
        this.auditor = auditor;
        this.codec = codec;
    }

    /** Start a lightweight watcher thread that injects when a connection appears. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        watcher = new Thread(this::loop, "packet-auditor-discovery");
        watcher.setDaemon(true);
        watcher.start();
        LOG.info("Connection self-discovery started.");
    }

    public void stop() {
        running = false;
        if (watcher != null) {
            watcher.interrupt();
        }
    }

    private void loop() {
        while (running) {
            try {
                Connection conn = currentConnection();
                if (conn != null && conn != injectedInto && conn.isConnected()) {
                    if (PacketAuditorInjector.inject(conn, auditor, codec)) {
                        injectedInto = conn;
                    }
                } else if (conn == null) {
                    injectedInto = null;
                }
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                LOG.debug("Discovery poll error: " + t.getMessage());
            }
        }
    }

    private Connection currentConnection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }
        ClientPacketListener listener = mc.getConnection();
        return listener == null ? null : listener.getConnection();
    }
}
