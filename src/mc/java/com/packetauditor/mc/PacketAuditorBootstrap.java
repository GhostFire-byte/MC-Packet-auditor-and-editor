package com.packetauditor.mc;

import com.packetauditor.api.AuditorConfig;
import com.packetauditor.api.PacketAuditor;
import com.packetauditor.mc.codec.CuratedAdapters;
import com.packetauditor.mc.codec.StreamCodecBridge;
import com.packetauditor.mc.discovery.ConnectionDiscovery;
import com.packetauditor.mc.render.CaptureExclusion;
import com.packetauditor.util.AuditorLog;

/**
 * One-call bootstrap for the Minecraft binding. Creates and starts a
 * {@link PacketAuditor}, registers the curated editable adapters for MC 26.2, and
 * (optionally) begins self-discovery of the active connection (D2 option C).
 *
 * <p>Your existing module system can instead construct {@link PacketAuditor}
 * directly and call {@link com.packetauditor.mc.net.PacketAuditorInjector} when a
 * connection opens — see the integration guide.</p>
 */
public final class PacketAuditorBootstrap {

    private static final AuditorLog LOG = AuditorLog.of(PacketAuditorBootstrap.class);

    private final PacketAuditor auditor;
    private final StreamCodecBridge codec = new StreamCodecBridge();
    private final ConnectionDiscovery discovery;

    public PacketAuditorBootstrap(AuditorConfig config) {
        this.auditor = new PacketAuditor(config);
        CuratedAdapters.registerDefaults(auditor.decoder());
        this.discovery = new ConnectionDiscovery(auditor, codec);
    }

    /** Start the module. If {@code selfDiscover} is true, auto-inject on connect. */
    public PacketAuditorBootstrap start(boolean selfDiscover) {
        auditor.start();
        if (auditor.config().excludeFromCapture()) {
            CaptureExclusion.excludeFromCapture();
        }
        if (selfDiscover) {
            discovery.start();
        }
        LOG.info("Packet Auditor bootstrap complete (selfDiscover=" + selfDiscover + ").");
        return this;
    }

    public void stop() {
        discovery.stop();
        auditor.stop();
    }

    public PacketAuditor auditor() {
        return auditor;
    }

    public StreamCodecBridge codec() {
        return codec;
    }

    public ConnectionDiscovery discovery() {
        return discovery;
    }
}
