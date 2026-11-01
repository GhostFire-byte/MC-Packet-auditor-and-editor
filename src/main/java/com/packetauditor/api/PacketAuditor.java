package com.packetauditor.api;

import com.packetauditor.anomaly.AnomalyDetectionService;
import com.packetauditor.api.event.ConnectionEvent;
import com.packetauditor.api.event.ConnectionEventType;
import com.packetauditor.api.event.ConnectionListener;
import com.packetauditor.codec.DecoderService;
import com.packetauditor.codec.PacketEditAdapter;
import com.packetauditor.delay.DelayManager;
import com.packetauditor.history.PacketHistory;
import com.packetauditor.match.MatchSpec;
import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.Direction;
import com.packetauditor.model.ProtocolPhase;
import com.packetauditor.netsim.NetworkSimulator;
import com.packetauditor.perf.PerformanceManager;
import com.packetauditor.pipeline.CaptureResult;
import com.packetauditor.pipeline.DelegatingTransport;
import com.packetauditor.pipeline.PacketPipeline;
import com.packetauditor.pipeline.PacketTransport;
import com.packetauditor.protect.ProtectionPolicy;
import com.packetauditor.queue.InterceptionQueue;
import com.packetauditor.rules.EditResult;
import com.packetauditor.rules.EditingRule;
import com.packetauditor.rules.EditingRuleEngine;
import com.packetauditor.stats.StatisticsCollector;
import com.packetauditor.store.JsonStore;
import com.packetauditor.store.ProfileCrypto;
import com.packetauditor.store.SqlitePacketStore;
import com.packetauditor.store.StoragePaths;
import com.packetauditor.ui.theme.ThemeManager;
import com.packetauditor.util.AuditorLog;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The public facade and lifecycle owner for the Packet Auditor module (D12).
 * This is the primary class your client / Codex interacts with:
 *
 * <pre>{@code
 * PacketAuditor auditor = new PacketAuditor(
 *     AuditorConfig.builder()
 *         .baseDir(myStorageDir)
 *         .encryptionKey(ProfileCrypto.hostKey(myKeyBytes))
 *         .build());
 * auditor.start();
 * // when a connection opens, from your Netty integration:
 * auditor.attach(myPacketTransport);
 * // per packet, from your Netty handler:
 * CaptureResult r = auditor.handleInbound(packet, id, size, phase, rawBytes);
 * }</pre>
 *
 * <p>Everything heavy is isolated in subsystems reachable via getters; the names
 * here are deliberately readable and are preserved by ProgGuard (D9).</p>
 */
public final class PacketAuditor {

    private static final AuditorLog LOG = AuditorLog.of(PacketAuditor.class);

    private final AuditorConfig config;
    private final StoragePaths paths;
    private final ProfileCrypto crypto;
    private final JsonStore jsonStore;

    private final ProtectionPolicy protection;
    private final DecoderService decoder;
    private final StatisticsCollector stats;
    private final PacketHistory history;
    private final AnomalyDetectionService anomaly;
    private final InterceptionQueue queue;
    private final DelayManager delay;
    private final NetworkSimulator netsim;
    private final EditingRuleEngine rules;
    private final PerformanceManager perf;
    private final ThemeManager themes;
    private final PacketPipeline pipeline;
    private final DelegatingTransport transport = new DelegatingTransport();

    private final CopyOnWriteArrayList<PacketHook> hooks = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Notification.Listener> notificationListeners = new CopyOnWriteArrayList<>();

    private volatile boolean started = false;
    private volatile boolean overlayVisible = false;
    private volatile SqlitePacketStore sqlite;

    public PacketAuditor(AuditorConfig config) {
        this.config = config == null ? AuditorConfig.builder().build() : config;

        AuditorLog.setMinLevel(this.config.logLevel());
        if (this.config.logSink() != null) {
            AuditorLog.setSink(this.config.logSink());
        }

        this.paths = this.config.baseDir() != null
                ? StoragePaths.underBase(this.config.baseDir())
                : StoragePaths.obscuredDefault();
        this.crypto = new ProfileCrypto(this.config.keyProvider());
        this.jsonStore = new JsonStore(paths, crypto);

        this.protection = new ProtectionPolicy();
        this.protection.setAdvancedMode(this.config.advancedModeDefault());

        this.decoder = new DecoderService();
        this.stats = new StatisticsCollector();
        this.history = new PacketHistory();
        this.history.setMaxEntries(this.config.historyMaxEntries());
        this.history.setMaxBytes(this.config.historyMaxBytes());
        this.anomaly = new AnomalyDetectionService(decoder);
        this.anomaly.registerBuiltIns();
        this.queue = new InterceptionQueue(transport);
        this.delay = new DelayManager();
        this.netsim = new NetworkSimulator();
        this.rules = new EditingRuleEngine(decoder, protection);
        this.perf = new PerformanceManager();
        this.perf.setGuiFps(this.config.guiFps());
        this.themes = new ThemeManager();
        this.themes.setCurrent(this.config.defaultTheme());

        this.pipeline = new PacketPipeline(protection, decoder, stats, history, anomaly,
                queue, delay, netsim, rules, perf);
        this.pipeline.setTransport(transport);
        this.pipeline.setCaptureEnabled(this.config.captureEnabledDefault());
        this.pipeline.setDiskPersistence(this.config.diskPersistenceDefault());

        // Wire modification history into the SQLite store (if enabled later).
        this.rules.setHistorySink(record -> {
            SqlitePacketStore db = sqlite;
            if (pipeline.diskPersistence() && db != null) {
                db.insertModificationAsync(record);
            }
        });
    }

    // ===================== lifecycle =====================

    /** Start the module: open the bulk store and begin accepting captures. */
    public synchronized void start() {
        if (started) {
            return;
        }
        if (config.enableSqlite()) {
            try {
                sqlite = new SqlitePacketStore(paths);
                sqlite.setMaxPacketRows(config.sqliteMaxRows());
                pipeline.setSqlite(sqlite);
            } catch (Exception e) {
                LOG.warn("SQLite store unavailable; disk persistence disabled: " + e.getMessage());
            }
        }
        started = true;
        LOG.info("Packet Auditor started. Storage base: " + paths.baseDir());
    }

    /** Stop the module and release resources. */
    public synchronized void stop() {
        if (!started) {
            return;
        }
        detach();
        queue.shutdown();
        perf.shutdown();
        if (sqlite != null) {
            sqlite.close();
            sqlite = null;
        }
        started = false;
        LOG.info("Packet Auditor stopped.");
    }

    /** Attach to a live connection's transport (D2 seam). */
    public void attach(PacketTransport channelTransport) {
        transport.setDelegate(channelTransport);
        pipeline.setActive(true);
        fireConnection(new ConnectionEvent(ConnectionEventType.CONNECT, "attached", -1));
    }

    /** Detach from the current connection. */
    public void detach() {
        if (transport.delegate() != null) {
            fireConnection(new ConnectionEvent(ConnectionEventType.DISCONNECT, "detached", -1));
        }
        transport.setDelegate(null);
        pipeline.setActive(false);
    }

    public boolean isAttached() {
        return transport.delegate() != null;
    }

    // ===================== capture entry points (called by the MC handler) =====================

    /** Process an inbound (SERVER→CLIENT) packet; applies post-receive hooks. */
    public CaptureResult handleInbound(Object nativePacket, int packetId, int sizeBytes,
                                       ProtocolPhase phase, byte[] rawBytes) {
        CaptureResult res = pipeline.process(nativePacket, Direction.INBOUND, packetId, sizeBytes,
                phase, rawBytes);
        if (res.action() != CaptureResult.Action.SUPPRESS && !hooks.isEmpty()) {
            Object fwd = res.action() == CaptureResult.Action.FORWARD_REPLACEMENT
                    ? res.replacement() : nativePacket;
            for (PacketHook h : hooks) {
                try {
                    h.onPostReceive(fwd, Direction.INBOUND);
                } catch (Throwable ignored) {
                }
            }
        }
        return res;
    }

    /** Process an outbound (CLIENT→SERVER) packet; applies pre-send hooks. */
    public CaptureResult handleOutbound(Object nativePacket, int packetId, int sizeBytes,
                                        ProtocolPhase phase, byte[] rawBytes) {
        Object p = nativePacket;
        for (PacketHook h : hooks) {
            try {
                Object r = h.onPreSend(p, Direction.OUTBOUND);
                if (r == null) {
                    return CaptureResult.suppress("dropped by pre-send hook");
                }
                p = r;
            } catch (Throwable ignored) {
            }
        }
        CaptureResult res = pipeline.process(p, Direction.OUTBOUND, packetId, sizeBytes, phase, rawBytes);
        if (res.action() == CaptureResult.Action.FORWARD_ORIGINAL && p != nativePacket) {
            return CaptureResult.forwardReplacement(p);
        }
        return res;
    }

    // ===================== simple helper methods (D12) =====================

    /** Manually edit a field of a captured packet (by sequence) in the structured editor. */
    public EditResult editPacketField(long sequence, String fieldPath, String value) {
        CapturedPacket packet = pipeline.findBySequence(sequence);
        if (packet == null) {
            EditResult r = EditResult.blocked(null,
                    "Packet #" + sequence + " is no longer in the live buffer.");
            notify(Notification.warn("Edit not saved", r.message()));
            return r;
        }
        EditResult result = rules.editField(packet, fieldPath, value);
        // Emit a clear save notification (your request): success / blocked / failure.
        if (result.applied()) {
            notify(Notification.success("Change saved",
                    packet.meta().packetName() + "." + fieldPath + " \u2192 " + value));
        } else {
            notify(Notification.warn("Change not applied",
                    packet.meta().packetName() + "." + fieldPath + ": " + result.message()));
        }
        return result;
    }

    /**
     * Dry-run a packet edit WITHOUT saving — lets your GUI warn the user before a
     * save that would fail or risk a crash. Returns applied=true if it would work.
     */
    public EditResult previewPacketEdit(long sequence, String fieldPath, String value) {
        CapturedPacket packet = pipeline.findBySequence(sequence);
        if (packet == null) {
            return EditResult.blocked(null,
                    "Packet #" + sequence + " is no longer in the live buffer.");
        }
        return rules.previewEdit(packet, fieldPath, value);
    }

    /** "Change all matching future packets": install an editing rule that sets a field. */
    public EditingRule changeAllMatching(String packetName, String fieldPath, String value) {
        MatchSpec spec = MatchSpec.builder().packetNameEquals(packetName).build();
        EditingRule rule = new EditingRule("Auto: " + packetName + "." + fieldPath, spec);
        rule.addReplacement(new com.packetauditor.rules.FieldReplacement(fieldPath, value));
        rules.addRule(rule);
        return rule;
    }

    /** "Intercept matching future packets": hold the next matching packets. */
    public void interceptMatching(MatchSpec spec) {
        pipeline.interceptRules().add(spec);
    }

    /** "Discard matching future packets" (subject to protection rules). */
    public void discardMatching(MatchSpec spec) {
        pipeline.discardRules().add(spec);
    }

    /** Register a reusable curated editable adapter (called by the MC integration). */
    public void registerEditAdapter(PacketEditAdapter adapter) {
        decoder.registerAdapter(adapter);
    }

    public void addHook(PacketHook hook) {
        if (hook != null) {
            hooks.add(hook);
        }
    }

    public void removeHook(PacketHook hook) {
        hooks.remove(hook);
    }

    public void addConnectionListener(ConnectionListener listener) {
        if (listener != null) {
            connectionListeners.add(listener);
        }
    }

    /** Subscribe to user-facing notifications (e.g. toast on packet-edit save). */
    public void addNotificationListener(Notification.Listener listener) {
        if (listener != null) {
            notificationListeners.add(listener);
        }
    }

    /** Emit a notification to all subscribers (safe; never throws). */
    public void notify(Notification notification) {
        if (notification == null) {
            return;
        }
        for (Notification.Listener l : notificationListeners) {
            try {
                l.onNotification(notification);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Emit a connection event (called by the MC integration for §4 monitoring). */
    public void fireConnection(ConnectionEvent event) {
        for (ConnectionListener l : connectionListeners) {
            try {
                l.onConnectionEvent(event);
            } catch (Throwable ignored) {
            }
        }
    }

    // ===================== overlay toggle (D5) =====================

    public void toggleOverlay() {
        setOverlayVisible(!overlayVisible);
    }

    public void setOverlayVisible(boolean visible) {
        this.overlayVisible = visible;
    }

    public boolean isOverlayVisible() {
        return overlayVisible;
    }

    public int toggleKey() {
        return config.toggleKey();
    }

    // ===================== accessors =====================

    public AuditorConfig config()            { return config; }
    public StoragePaths paths()              { return paths; }
    public JsonStore jsonStore()             { return jsonStore; }
    public ProtectionPolicy protection()     { return protection; }
    public DecoderService decoder()          { return decoder; }
    public StatisticsCollector stats()       { return stats; }
    public PacketHistory history()           { return history; }
    public AnomalyDetectionService anomaly() { return anomaly; }
    public InterceptionQueue queue()         { return queue; }
    public DelayManager delay()              { return delay; }
    public NetworkSimulator netsim()         { return netsim; }
    public EditingRuleEngine rules()         { return rules; }
    public PerformanceManager performance()  { return perf; }
    public ThemeManager themes()             { return themes; }
    public PacketPipeline pipeline()         { return pipeline; }
    public SqlitePacketStore sqlite()        { return sqlite; }
    public boolean isStarted()               { return started; }
}
