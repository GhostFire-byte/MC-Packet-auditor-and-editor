package com.packetauditor.api;

import com.packetauditor.anomaly.Anomaly;
import com.packetauditor.model.CapturedPacket;
import com.packetauditor.perf.PerformanceManager;
import com.packetauditor.queue.QueueStatus;
import com.packetauditor.stats.TrafficSnapshot;
import com.packetauditor.ui.PacketRow;
import com.packetauditor.ui.UiDataSource;
import com.packetauditor.ui.theme.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts a {@link PacketAuditor} to the UI's read-only {@link UiDataSource}. Use
 * this to render the bundled {@code OverlayView}/{@code DashboardView} — or
 * implement {@link UiDataSource} yourself to feed your own GUI from the same
 * backend.
 */
public final class AuditorUiDataSource implements UiDataSource {

    private final PacketAuditor auditor;

    public AuditorUiDataSource(PacketAuditor auditor) {
        this.auditor = auditor;
    }

    @Override
    public Theme theme() {
        return auditor.themes().current();
    }

    @Override
    public TrafficSnapshot traffic() {
        return auditor.stats().snapshot(3);
    }

    @Override
    public QueueStatus queueStatus() {
        return auditor.queue().status();
    }

    @Override
    public List<PacketRow> recentPackets(int limit) {
        List<CapturedPacket> live = auditor.pipeline().recentLive(limit);
        List<PacketRow> rows = new ArrayList<>(live.size());
        for (CapturedPacket p : live) {
            var m = p.meta();
            rows.add(new PacketRow(
                    m.sequence(), m.direction().isInbound(), m.direction().shortLabel(),
                    m.packetName(), m.sizeBytes(), m.phase().name(), m.timestampMillis(),
                    false, auditor.anomaly().markerFor(m.sequence())));
        }
        return rows;
    }

    @Override
    public List<String> recentAnomalies(int limit) {
        List<Anomaly> all = auditor.anomaly().history();
        List<String> out = new ArrayList<>();
        int from = Math.max(0, all.size() - limit);
        for (int i = all.size() - 1; i >= from; i--) {
            out.add(all.get(i).toString());
        }
        return out;
    }

    @Override
    public PerformanceManager.ResourceSnapshot resources() {
        return auditor.performance().sampleResources(auditor.queue().size());
    }

    @Override
    public long totalProcessed() {
        return auditor.pipeline().totalProcessed();
    }

    @Override
    public boolean advancedMode() {
        return auditor.protection().isAdvancedMode();
    }

    @Override
    public boolean unlockAllProtected() {
        return auditor.protection().isUnlockAllProtected();
    }

    @Override
    public boolean captureEnabled() {
        return auditor.pipeline().captureEnabled();
    }

    @Override
    public com.packetauditor.ui.PacketDetail packetDetail(long sequence) {
        CapturedPacket packet = auditor.pipeline().findBySequence(sequence);
        if (packet == null) {
            return null;
        }
        com.packetauditor.model.DecodedPacket decoded;
        try {
            decoded = auditor.decoder().decode(packet);
        } catch (Throwable t) {
            decoded = com.packetauditor.model.DecodedPacket.rawOnly("decode failed");
        }
        List<com.packetauditor.ui.FieldView> fields = new ArrayList<>();
        for (com.packetauditor.model.PacketField f : decoded.fields()) {
            fields.add(new com.packetauditor.ui.FieldView(f.name(), f.path(), f.type().name(),
                    f.displayValue(), f.editable(), f.notEditableReason()));
        }
        var m = packet.meta();
        return new com.packetauditor.ui.PacketDetail(m.sequence(), m.packetName(),
                m.direction().displayName(), m.phase().name(), m.sizeBytes(),
                decoded.source().name(), decoded.editable(), decoded.notEditableReason(), fields);
    }

    @Override
    public List<com.packetauditor.ui.HeldView> heldPackets() {
        List<com.packetauditor.ui.HeldView> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (var hp : auditor.queue().snapshot()) {
            var m = hp.packet().meta();
            out.add(new com.packetauditor.ui.HeldView(hp.id(), m.packetName(),
                    m.direction().shortLabel(), m.direction().isInbound(), hp.ageMillis(now)));
        }
        return out;
    }

    @Override
    public boolean delayEnabled() {
        return auditor.delay().enabled();
    }

    @Override
    public boolean netsimEnabled() {
        return auditor.netsim().enabled();
    }

    @Override
    public double inboundLoss() {
        return auditor.netsim().inboundLossProbability();
    }

    @Override
    public double outboundLoss() {
        return auditor.netsim().outboundLossProbability();
    }

    @Override
    public long inboundThrottleBps() {
        return auditor.netsim().inboundBytesPerSecond();
    }

    @Override
    public long outboundThrottleBps() {
        return auditor.netsim().outboundBytesPerSecond();
    }

    @Override
    public List<com.packetauditor.ui.RuleView> rules() {
        List<com.packetauditor.ui.RuleView> out = new ArrayList<>();
        for (var r : auditor.rules().rules()) {
            out.add(new com.packetauditor.ui.RuleView(r.id(), com.packetauditor.ui.RuleView.Kind.EDIT,
                    r.name() + "  (pri " + r.priority() + ")", r.enabled()));
        }
        for (var p : auditor.delay().profiles()) {
            out.add(new com.packetauditor.ui.RuleView(p.id(), com.packetauditor.ui.RuleView.Kind.DELAY,
                    p.name(), p.enabled()));
        }
        return out;
    }

    @Override
    public int interceptRuleCount() {
        return auditor.pipeline().interceptRules().size();
    }

    @Override
    public int discardRuleCount() {
        return auditor.pipeline().discardRules().size();
    }
}
