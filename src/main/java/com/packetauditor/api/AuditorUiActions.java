package com.packetauditor.api;

import com.packetauditor.delay.DelayProfile;
import com.packetauditor.match.MatchSpec;
import com.packetauditor.model.CapturedPacket;
import com.packetauditor.rules.EditResult;
import com.packetauditor.rules.EditingRule;
import com.packetauditor.rules.FieldReplacement;
import com.packetauditor.ui.UiActions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Adapts {@link UiActions} (the view write-side) to a {@link PacketAuditor}.
 * Packet selection / action-menu callbacks are delegated to host-supplied
 * handlers so your GUI decides how to present the per-packet action menu
 * (inspect / edit / change-all / intercept / delay / discard).
 */
public final class AuditorUiActions implements UiActions {

    private final PacketAuditor auditor;
    private final LongConsumer onSelect;
    private final LongConsumer onActionMenu;

    public AuditorUiActions(PacketAuditor auditor) {
        this(auditor, seq -> { }, seq -> { });
    }

    public AuditorUiActions(PacketAuditor auditor, LongConsumer onSelect, LongConsumer onActionMenu) {
        this.auditor = auditor;
        this.onSelect = onSelect == null ? seq -> { } : onSelect;
        this.onActionMenu = onActionMenu == null ? seq -> { } : onActionMenu;
    }

    @Override
    public void setCaptureEnabled(boolean enabled) {
        auditor.pipeline().setCaptureEnabled(enabled);
        auditor.notify(Notification.info("Capture " + (enabled ? "on" : "off"),
                enabled ? "Observing packet traffic." : "Packet capture paused."));
    }

    @Override
    public void setAdvancedMode(boolean enabled) {
        auditor.protection().setAdvancedMode(enabled);
        auditor.notify(Notification.info("Advanced Mode " + (enabled ? "on" : "off"),
                enabled ? "Non-essential packets can now be modified."
                        : "Editing of non-essential packets disabled."));
    }

    @Override
    public void setUnlockAllProtected(boolean enabled) {
        auditor.protection().setUnlockAllProtected(enabled);
        if (enabled) {
            auditor.notify(Notification.warn("Unlock ALL enabled",
                    "Protected packets (KeepAlive/Pong/login) can now be modified \u2014 this may disconnect you."));
        } else {
            auditor.notify(Notification.info("Unlock ALL disabled",
                    "Connection-integrity packets are protected again."));
        }
    }

    @Override
    public void selectPacket(long sequence) {
        onSelect.accept(sequence);
    }

    @Override
    public void openPacketActionMenu(long sequence) {
        onActionMenu.accept(sequence);
    }

    @Override
    public void cycleTheme() {
        List<String> names = new ArrayList<>(auditor.themes().themeNames());
        if (names.isEmpty()) {
            return;
        }
        String current = auditor.themes().current().name;
        int idx = names.indexOf(current);
        String next = names.get((idx + 1) % names.size());
        auditor.themes().setCurrent(next);
        auditor.notify(Notification.info("Theme", next));
    }

    @Override
    public void releaseAllHeld() {
        int n = auditor.queue().releaseAll();
        auditor.notify(Notification.info("Queue", "Released " + n + " held packet(s)."));
    }

    @Override
    public void discardAllHeld() {
        int n = auditor.queue().discardAll();
        auditor.notify(Notification.warn("Queue", "Discarded " + n + " held packet(s)."));
    }

    // ----- per-packet actions -----

    @Override
    public void inspect(long sequence) {
        onSelect.accept(sequence);
    }

    @Override
    public void editPacket(long sequence) {
        onSelect.accept(sequence);
    }

    @Override
    public void interceptMatchingType(long sequence) {
        String name = nameOf(sequence);
        if (name == null) {
            return;
        }
        auditor.interceptMatching(MatchSpec.builder().packetNameEquals(name).build());
        auditor.notify(Notification.info("Intercept armed",
                "Future " + name + " packets will be held for review."));
    }

    @Override
    public void delayMatchingType(long sequence) {
        String name = nameOf(sequence);
        if (name == null) {
            return;
        }
        DelayProfile profile = new DelayProfile("Delay " + name)
                .setEnabled(true).setMode(DelayProfile.Mode.FIXED).setFixedMillis(200)
                .setPacketFilter(MatchSpec.builder().packetNameEquals(name).build());
        auditor.delay().addProfile(profile);
        auditor.delay().setEnabled(true);
        auditor.notify(Notification.info("Delay armed", "Future " + name + " packets delayed 200ms."));
    }

    @Override
    public void discardMatchingType(long sequence) {
        String name = nameOf(sequence);
        if (name == null) {
            return;
        }
        auditor.discardMatching(MatchSpec.builder().packetNameEquals(name).build());
        auditor.notify(Notification.warn("Discard armed",
                "Future " + name + " packets will be discarded (subject to protection)."));
    }

    @Override
    public String applyEditToAllMatching(long sequence, String fieldPath, String value) {
        String name = nameOf(sequence);
        if (name == null) {
            return "Packet is no longer available.";
        }
        EditingRule rule = new EditingRule("Auto: " + name + "." + fieldPath,
                MatchSpec.builder().packetNameEquals(name).build());
        rule.addReplacement(new FieldReplacement(fieldPath, value));
        auditor.rules().addRule(rule);
        auditor.notify(Notification.success("Rule added",
                "All future " + name + " will set " + fieldPath + " = " + value));
        return null;
    }

    @Override
    public String saveFieldEdit(long sequence, String fieldPath, String value) {
        EditResult r = auditor.editPacketField(sequence, fieldPath, value); // emits a toast
        return r.applied() ? null : r.message();
    }

    @Override
    public String previewFieldEdit(long sequence, String fieldPath, String value) {
        EditResult r = auditor.previewPacketEdit(sequence, fieldPath, value);
        return r.applied() ? null : r.message();
    }

    @Override
    public void releaseHeld(long id) {
        if (auditor.queue().release(id)) {
            auditor.notify(Notification.info("Queue", "Released held packet #" + id + "."));
        }
    }

    @Override
    public void discardHeld(long id) {
        if (auditor.queue().discard(id)) {
            auditor.notify(Notification.warn("Queue", "Discarded held packet #" + id + "."));
        }
    }

    private String nameOf(long sequence) {
        CapturedPacket p = auditor.pipeline().findBySequence(sequence);
        return p == null ? null : p.meta().packetName();
    }

    // ----- tools -----

    @Override
    public void setDelayEnabled(boolean enabled) {
        auditor.delay().setEnabled(enabled);
        auditor.notify(Notification.info("Delay " + (enabled ? "on" : "off"), ""));
    }

    @Override
    public void setNetsimEnabled(boolean enabled) {
        auditor.netsim().setEnabled(enabled);
        auditor.notify(Notification.warn("Network sim " + (enabled ? "on" : "off"),
                enabled ? "Throttle / simulated loss active for configured rates." : ""));
    }

    @Override
    public void setInboundLoss(double probability) {
        auditor.netsim().setInboundLossProbability(probability);
    }

    @Override
    public void setOutboundLoss(double probability) {
        auditor.netsim().setOutboundLossProbability(probability);
    }

    @Override
    public void setInboundThrottle(long bytesPerSecond) {
        auditor.netsim().setInboundBytesPerSecond(bytesPerSecond);
    }

    @Override
    public void setOutboundThrottle(long bytesPerSecond) {
        auditor.netsim().setOutboundBytesPerSecond(bytesPerSecond);
    }

    @Override
    public void toggleRule(String id, boolean enabled) {
        for (var r : auditor.rules().rules()) {
            if (r.id().equals(id)) {
                r.setEnabled(enabled);
                return;
            }
        }
        for (var p : auditor.delay().profiles()) {
            if (p.id().equals(id)) {
                p.setEnabled(enabled);
                return;
            }
        }
    }

    @Override
    public void deleteRule(String id) {
        auditor.rules().removeRule(id);
        auditor.delay().removeProfile(id);
        auditor.notify(Notification.info("Rule removed", id));
    }

    @Override
    public void clearInterceptRules() {
        auditor.pipeline().interceptRules().clear();
        auditor.notify(Notification.info("Intercept rules cleared", ""));
    }

    @Override
    public void clearDiscardRules() {
        auditor.pipeline().discardRules().clear();
        auditor.notify(Notification.info("Discard rules cleared", ""));
    }

    @Override
    public String exportHistoryJson() {
        try {
            java.nio.file.Path file = auditor.paths().baseDir()
                    .resolve("export-" + System.currentTimeMillis() + ".json");
            com.packetauditor.history.HistoryExporter.exportJson(auditor.history().all(), file);
            auditor.notify(Notification.success("Exported JSON", file.toString()));
            return null;
        } catch (Exception e) {
            auditor.notify(Notification.error("Export failed", e.getMessage()));
            return e.getMessage();
        }
    }

    @Override
    public String exportHistoryCsv() {
        try {
            java.nio.file.Path file = auditor.paths().baseDir()
                    .resolve("export-" + System.currentTimeMillis() + ".csv");
            com.packetauditor.history.HistoryExporter.exportCsv(auditor.history().all(), file);
            auditor.notify(Notification.success("Exported CSV", file.toString()));
            return null;
        } catch (Exception e) {
            auditor.notify(Notification.error("Export failed", e.getMessage()));
            return e.getMessage();
        }
    }
}
