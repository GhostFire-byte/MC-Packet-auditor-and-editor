package com.packetauditor.ui.view;

import com.packetauditor.perf.PerformanceManager;
import com.packetauditor.queue.QueueStatus;
import com.packetauditor.stats.TrafficSnapshot;
import com.packetauditor.ui.FieldView;
import com.packetauditor.ui.HeldView;
import com.packetauditor.ui.InputEvent;
import com.packetauditor.ui.PacketDetail;
import com.packetauditor.ui.PacketRow;
import com.packetauditor.ui.Rect;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.UiActions;
import com.packetauditor.ui.UiColor;
import com.packetauditor.ui.UiDataSource;
import com.packetauditor.ui.component.Button;
import com.packetauditor.ui.component.Component;
import com.packetauditor.ui.component.ScrollListView;
import com.packetauditor.ui.component.TextField;
import com.packetauditor.ui.component.ToggleSwitch;
import com.packetauditor.ui.theme.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * The full-featured dashboard (spec §14): tabbed so functionality stays
 * uncluttered (Packets / Inspect+Edit / Queue / Traffic / Anomalies / Tools),
 * with a right-click action menu, inline field editing (with preview + save +
 * "apply to all"), interception-queue management and a tools panel. Supports a
 * <b>compact docked</b> layout (set via {@link #setCompact}) so it stays small and
 * non-covering when a Minecraft inventory/container screen is open.
 */
public final class DashboardView extends Component {

    private static final String[] TABS = {"Packets", "Inspect", "Queue", "Traffic", "Anomalies", "Rules", "Tools"};
    private static final int HEADER = 18;
    private static final int TABBAR = 15;
    private static final int FOOTER = 15;

    private final UiDataSource data;
    private final UiActions actions;

    private final ScrollListView<PacketRow> packetList;
    private final ScrollListView<HeldView> heldList;
    private final ScrollListView<com.packetauditor.ui.RuleView> rulesList;
    private final ActionMenu actionMenu = new ActionMenu();

    private final ToggleSwitch captureToggle;
    private final ToggleSwitch advancedToggle;
    private final ToggleSwitch unlockToggle;
    private final ToggleSwitch delayToggle;
    private final ToggleSwitch netsimToggle;
    private final Button themeBtn;
    private final Button exportJsonBtn;
    private final Button exportCsvBtn;
    private final Button releaseAllBtn;
    private final Button discardAllBtn;
    private final Button saveBtn;
    private final Button previewBtn;
    private final Button applyAllBtn;
    private final Button clearInterceptBtn;
    private final Button clearDiscardBtn;
    private final com.packetauditor.ui.component.Slider lossInSlider;
    private final com.packetauditor.ui.component.Slider lossOutSlider;
    private final com.packetauditor.ui.component.Slider throttleInSlider;
    private final com.packetauditor.ui.component.Slider throttleOutSlider;

    private final List<Component> children = new ArrayList<>();

    // Drag / resize state
    private enum Drag { NONE, MOVE, RESIZE }
    private Drag drag = Drag.NONE;
    private Integer manualX, manualY, manualW, manualH;
    private double dragOffX, dragOffY, startMouseX, startMouseY;
    private int startW, startH;

    // Editor state
    private final List<EditorField> editorFields = new ArrayList<>();
    private long editorBuiltSeq = Long.MIN_VALUE;
    private String editorStatus = "";
    private UiColor editorStatusColor;

    private int activeTab = 0;
    private long selectedSeq = -1;
    private boolean compact = false;
    private double lastMouseX, lastMouseY;
    private final int[] tabStart = new int[TABS.length];
    private final int[] tabWidth = new int[TABS.length];

    public DashboardView(UiDataSource data, UiActions actions) {
        this.data = data;
        this.actions = actions;

        this.packetList = new ScrollListView<>(() -> data.recentPackets(500), this::renderPacketRow)
                .rowHeight(12)
                .onSelect(row -> selectedSeq = row.sequence)
                .onActivate(row -> openActionMenu(row));

        this.heldList = new ScrollListView<>(data::heldPackets, this::renderHeldRow)
                .rowHeight(12)
                .onSelect(h -> actions.releaseHeld(h.id))
                .onActivate(h -> actions.discardHeld(h.id));

        this.rulesList = new ScrollListView<>(data::rules, this::renderRuleRow)
                .rowHeight(12)
                .onSelect(rv -> actions.toggleRule(rv.id, !rv.enabled))
                .onActivate(rv -> actions.deleteRule(rv.id));

        this.captureToggle = new ToggleSwitch("Capture", data::captureEnabled, actions::setCaptureEnabled);
        this.advancedToggle = new ToggleSwitch("Advanced Mode", data::advancedMode, actions::setAdvancedMode);
        this.unlockToggle = new ToggleSwitch("Unlock ALL (risky)", data::unlockAllProtected, actions::setUnlockAllProtected);
        this.delayToggle = new ToggleSwitch("Delay system", data::delayEnabled, actions::setDelayEnabled);
        this.netsimToggle = new ToggleSwitch("Network sim", data::netsimEnabled, actions::setNetsimEnabled);
        this.themeBtn = new Button(() -> "Theme: " + data.theme().name, actions::cycleTheme);
        this.exportJsonBtn = new Button("Export JSON", actions::exportHistoryJson);
        this.exportCsvBtn = new Button("Export CSV", actions::exportHistoryCsv);
        this.releaseAllBtn = new Button("Release all", actions::releaseAllHeld);
        this.discardAllBtn = new Button("Discard all", actions::discardAllHeld);
        this.saveBtn = new Button("Save", this::doSave);
        this.previewBtn = new Button("Preview", this::doPreview);
        this.applyAllBtn = new Button("Apply to all", this::doApplyAll);
        this.clearInterceptBtn = new Button("Clear intercept", actions::clearInterceptRules);
        this.clearDiscardBtn = new Button("Clear discard", actions::clearDiscardRules);

        this.lossInSlider = new com.packetauditor.ui.component.Slider("Loss in %", 0, 100,
                () -> data.inboundLoss() * 100, v -> actions.setInboundLoss(v / 100.0))
                .format(v -> String.format("%.0f%%", v));
        this.lossOutSlider = new com.packetauditor.ui.component.Slider("Loss out %", 0, 100,
                () -> data.outboundLoss() * 100, v -> actions.setOutboundLoss(v / 100.0))
                .format(v -> String.format("%.0f%%", v));
        this.throttleInSlider = new com.packetauditor.ui.component.Slider("Throttle in KB/s", 0, 1024,
                () -> data.inboundThrottleBps() / 1024.0, v -> actions.setInboundThrottle((long) (v * 1024)))
                .format(v -> v <= 0 ? "off" : String.format("%.0f KB/s", v));
        this.throttleOutSlider = new com.packetauditor.ui.component.Slider("Throttle out KB/s", 0, 1024,
                () -> data.outboundThrottleBps() / 1024.0, v -> actions.setOutboundThrottle((long) (v * 1024)))
                .format(v -> v <= 0 ? "off" : String.format("%.0f KB/s", v));

        children.add(packetList);
        children.add(heldList);
        children.add(rulesList);
        children.add(captureToggle);
        children.add(advancedToggle);
        children.add(unlockToggle);
        children.add(delayToggle);
        children.add(netsimToggle);
        children.add(themeBtn);
        children.add(exportJsonBtn);
        children.add(exportCsvBtn);
        children.add(releaseAllBtn);
        children.add(discardAllBtn);
        children.add(saveBtn);
        children.add(previewBtn);
        children.add(applyAllBtn);
        children.add(clearInterceptBtn);
        children.add(clearDiscardBtn);
        children.add(lossInSlider);
        children.add(lossOutSlider);
        children.add(throttleInSlider);
        children.add(throttleOutSlider);
    }

    /** Compact docked layout (used when a Minecraft screen is open). */
    public void setCompact(boolean compact) {
        this.compact = compact;
    }

    public boolean compact() {
        return compact;
    }

    // ---------------- layout ----------------

    private Rect contentRect() {
        int top = bounds.y + HEADER + TABBAR;
        int bottom = bounds.bottom() - FOOTER;
        return new Rect(bounds.x + 6, top + 2, bounds.width - 12, bottom - top - 4);
    }

    private void computeBounds(Renderer r) {
        int sw = r.screenWidth();
        int sh = r.screenHeight();
        if (manualX != null) {
            int w = clampInt(manualW, 240, sw);
            int h = clampInt(manualH, 160, sh);
            int x = clampInt(manualX, 0, sw - w);
            int y = clampInt(manualY, 0, sh - h);
            manualW = w; manualH = h; manualX = x; manualY = y;
            setBounds(new Rect(x, y, w, h));
        } else if (compact) {
            int w = Math.min(360, (int) (sw * 0.42));
            setBounds(new Rect(sw - w - 6, 6, w, sh - 12));
        } else {
            setBounds(new Rect(8, 8, sw - 16, sh - 16));
        }
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(Math.max(lo, hi), v));
    }

    private Rect resizeGrip() {
        return new Rect(bounds.right() - 10, bounds.bottom() - 10, 10, 10);
    }

    private void layout() {
        for (Component c : children) {
            c.setVisible(false);
        }
        Rect content = contentRect();

        switch (activeTab) {
            case 0: // Packets
                packetList.setVisible(true);
                packetList.setBounds(content);
                break;
            case 1: // Inspect / Edit
                layoutEditor(content);
                break;
            case 2: // Queue
                heldList.setVisible(true);
                heldList.setBounds(new Rect(content.x, content.y + 11, content.width, content.height - 29));
                releaseAllBtn.setVisible(true);
                discardAllBtn.setVisible(true);
                releaseAllBtn.setBounds(new Rect(content.x, content.bottom() - 15, 80, 13));
                discardAllBtn.setBounds(new Rect(content.x + 86, content.bottom() - 15, 80, 13));
                break;
            case 5: // Rules
                rulesList.setVisible(true);
                rulesList.setBounds(new Rect(content.x, content.y + 11, content.width, content.height - 29));
                clearInterceptBtn.setVisible(true);
                clearDiscardBtn.setVisible(true);
                clearInterceptBtn.setBounds(new Rect(content.x, content.bottom() - 15, 104, 13));
                clearDiscardBtn.setBounds(new Rect(content.x + 110, content.bottom() - 15, 96, 13));
                break;
            case 6: // Tools
                layoutTools(content);
                break;
            default:
                break; // Traffic / Anomalies are immediate-draw only
        }
    }

    private void layoutTools(Rect c) {
        int x = c.x;
        int y = c.y + 2;
        int w = Math.min(200, c.width - 8);
        for (ToggleSwitch t : new ToggleSwitch[]{captureToggle, advancedToggle, unlockToggle, delayToggle, netsimToggle}) {
            t.setVisible(true);
            t.setBounds(new Rect(x, y, w, 12));
            y += 15;
        }
        y += 4;
        themeBtn.setVisible(true);
        themeBtn.setBounds(new Rect(x, y, w, 13));
        y += 16;
        exportJsonBtn.setVisible(true);
        exportCsvBtn.setVisible(true);
        exportJsonBtn.setBounds(new Rect(x, y, (w - 6) / 2, 13));
        exportCsvBtn.setBounds(new Rect(x + (w - 6) / 2 + 6, y, (w - 6) / 2, 13));
        y += 18;
        for (com.packetauditor.ui.component.Slider s :
                new com.packetauditor.ui.component.Slider[]{lossInSlider, lossOutSlider, throttleInSlider, throttleOutSlider}) {
            if (y + 16 > c.bottom()) {
                s.setVisible(false);
            } else {
                s.setVisible(true);
                s.setBounds(new Rect(x, y, w, 15));
            }
            y += 20;
        }
    }

    private void layoutEditor(Rect c) {
        rebuildEditorIfNeeded();
        int fieldTop = c.y + 56;
        int rowH = 15;
        int labelW = Math.min(120, c.width / 3);
        int fieldX = c.x + labelW + 6;
        int fieldW = c.width - labelW - 10;
        int i = 0;
        for (EditorField ef : editorFields) {
            int y = fieldTop + i * rowH;
            if (y > c.bottom() - 40) {
                ef.field.setVisible(false);
            } else {
                ef.field.setVisible(true);
                ef.field.setBounds(new Rect(fieldX, y, fieldW, 12));
            }
            i++;
        }
        boolean hasEditable = !editorFields.isEmpty();
        int by = c.bottom() - 16;
        saveBtn.setVisible(hasEditable);
        previewBtn.setVisible(hasEditable);
        applyAllBtn.setVisible(hasEditable);
        saveBtn.setBounds(new Rect(c.x, by, 52, 14));
        previewBtn.setBounds(new Rect(c.x + 56, by, 60, 14));
        applyAllBtn.setBounds(new Rect(c.x + 120, by, 80, 14));
    }

    private void rebuildEditorIfNeeded() {
        if (selectedSeq == editorBuiltSeq) {
            return;
        }
        editorBuiltSeq = selectedSeq;
        editorFields.clear();
        editorStatus = "";
        PacketDetail detail = selectedSeq < 0 ? null : data.packetDetail(selectedSeq);
        if (detail == null) {
            return;
        }
        for (FieldView f : detail.fields) {
            if (f.editable) {
                TextField tf = new TextField(f.value).placeholder(f.type);
                editorFields.add(new EditorField(f.path, f.value, tf));
            }
        }
    }

    // ---------------- render ----------------

    @Override
    public void render(Renderer r, Theme theme) {
        if (!visible) {
            return;
        }
        computeBounds(r);
        layout();

        Views.panelBackground(r, theme, bounds, true);

        // Header
        r.fillRect(new Rect(bounds.x, bounds.y, bounds.width, 2), theme.accent);
        r.drawText("PACKET AUDITOR", bounds.x + 8, bounds.y + 5, theme.text, true);
        if (!compact) {
            r.drawText("MC 26.2", bounds.x + 8 + r.textWidth("PACKET AUDITOR  "), bounds.y + 5, theme.accent, false);
        }
        String dot = data.captureEnabled() ? "\u25CF REC" : "\u25CB idle";
        r.drawText(dot, bounds.right() - r.textWidth(dot) - 8, bounds.y + 5,
                data.captureEnabled() ? theme.inbound : theme.textMuted, false);

        // Tab bar
        renderTabs(r, theme);

        // Active tab
        switch (activeTab) {
            case 0: renderPacketsTab(r, theme); break;
            case 1: renderInspectTab(r, theme); break;
            case 2: renderQueueTab(r, theme); break;
            case 3: renderTrafficTab(r, theme); break;
            case 4: renderAnomaliesTab(r, theme); break;
            case 5: renderRulesTab(r, theme); break;
            case 6: renderToolsTab(r, theme); break;
            default: break;
        }

        renderFooter(r, theme);
        // Resize grip (bottom-right).
        Rect grip = resizeGrip();
        r.drawLine(grip.x + 2, grip.bottom() - 1, grip.right() - 1, grip.y + 2, theme.textMuted, 1);
        r.drawLine(grip.x + 5, grip.bottom() - 1, grip.right() - 1, grip.y + 5, theme.textMuted, 1);
        actionMenu.render(r, theme);
    }

    private void renderTabs(Renderer r, Theme theme) {
        int y = bounds.y + HEADER;
        r.fillRect(new Rect(bounds.x, y, bounds.width, TABBAR), theme.panelAlt.scaleAlpha(theme.opacity));
        int x = bounds.x + 4;
        for (int i = 0; i < TABS.length; i++) {
            String title = TABS[i];
            int w = r.textWidth(title) + 10;
            tabStart[i] = x;
            tabWidth[i] = w;
            boolean active = i == activeTab;
            if (active) {
                r.fillRect(new Rect(x, y + TABBAR - 2, w, 2), theme.accent);
            }
            r.drawText(title, x + 5, y + 4, active ? theme.text : theme.textMuted, false);
            x += w;
        }
        r.drawLine(bounds.x, y + TABBAR, bounds.right(), y + TABBAR, theme.border, 1);
    }

    private void renderPacketsTab(Renderer r, Theme theme) {
        packetList.render(r, theme);
    }

    private void renderInspectTab(Renderer r, Theme theme) {
        Rect c = contentRect();
        if (selectedSeq < 0) {
            r.drawText("Select a packet in the Packets tab, then inspect/edit it here.",
                    c.x, c.y + 4, theme.textMuted, false);
            return;
        }
        PacketDetail d = data.packetDetail(selectedSeq);
        if (d == null) {
            r.drawText("Packet #" + selectedSeq + " is no longer in the live buffer.",
                    c.x, c.y + 4, theme.warn, false);
            return;
        }
        int y = c.y + 2;
        r.drawText(d.name, c.x, y, theme.text, true);
        y += 11;
        r.drawText(d.directionLabel + "  |  " + d.phase + "  |  " + (d.size >= 0 ? d.size + "B" : "?")
                + "  |  " + d.decodeSource, c.x, y, theme.textMuted, false);
        y += 11;
        if (!d.editable) {
            r.drawText("Inspect-only: " + (d.notEditableReason == null ? "not editable" : d.notEditableReason),
                    c.x, y, theme.warn, false);
        } else {
            r.drawText("Editable fields \u2014 type a value, then Preview / Save / Apply-to-all:",
                    c.x, y, theme.accent, false);
        }

        // Field labels + the retained TextFields (positioned in layout()).
        int labelW = Math.min(120, c.width / 3);
        int i = 0;
        int fieldTop = c.y + 56;
        for (EditorField ef : editorFields) {
            int fy = fieldTop + i * 15;
            if (ef.field.visible()) {
                r.drawText(ef.path, c.x, fy + 2, theme.text, false);
                ef.field.render(r, theme);
            }
            i++;
        }
        // Read-only fields listing (compact), below editable ones.
        int roY = fieldTop + editorFields.size() * 15 + 2;
        for (FieldView f : d.fields) {
            if (f.editable || roY > c.bottom() - 22) {
                continue;
            }
            r.drawText(Views.truncate(r, f.name + " = " + f.value, c.width - 8), c.x, roY, theme.textMuted, false);
            roY += 10;
        }

        if (!editorStatus.isEmpty()) {
            r.drawText(Views.truncate(r, editorStatus, c.width - 8), c.x, c.bottom() - 28,
                    editorStatusColor == null ? theme.textMuted : editorStatusColor, false);
        }
        saveBtn.render(r, theme);
        previewBtn.render(r, theme);
        applyAllBtn.render(r, theme);
    }

    private void renderQueueTab(Renderer r, Theme theme) {
        Rect c = contentRect();
        QueueStatus q = data.queueStatus();
        r.drawText("Held " + q.currentSize() + "/" + q.capacity()
                + "   released " + q.totalReleased() + "   discarded " + q.totalDiscarded()
                + "   (left-click = release, right-click = discard)", c.x, c.y, theme.textMuted, false);
        heldList.render(r, theme);
        releaseAllBtn.render(r, theme);
        discardAllBtn.render(r, theme);
    }

    private void renderTrafficTab(Renderer r, Theme theme) {
        Rect c = contentRect();
        TrafficSnapshot t = data.traffic();
        int y = c.y + 2;
        y = kv(r, theme, c.x, y, "Total packets", t.totalPackets() + "  (in " + t.packetsIn() + " / out " + t.packetsOut() + ")");
        y = kv(r, theme, c.x, y, "In rate", String.format("%.1f pkt/s   %s/s", t.packetsInPerSecond(), Views.humanBytes((long) t.bytesInPerSecond())));
        y = kv(r, theme, c.x, y, "Out rate", String.format("%.1f pkt/s   %s/s", t.packetsOutPerSecond(), Views.humanBytes((long) t.bytesOutPerSecond())));
        y = kv(r, theme, c.x, y, "Total data", Views.humanBytes(t.totalBytes()) + "  (in " + Views.humanBytes(t.bytesIn()) + " / out " + Views.humanBytes(t.bytesOut()) + ")");
        y = kv(r, theme, c.x, y, "Processed", String.valueOf(data.totalProcessed()));
    }

    private void renderAnomaliesTab(Renderer r, Theme theme) {
        Rect c = contentRect();
        List<String> anomalies = data.recentAnomalies(compact ? 14 : 30);
        if (anomalies.isEmpty()) {
            r.drawText("No anomalies detected.", c.x, c.y + 2, theme.textMuted, false);
            return;
        }
        int y = c.y + 2;
        for (String a : anomalies) {
            if (y > c.bottom() - 4) {
                break;
            }
            UiColor col = a.contains("CRITICAL") ? theme.critical : a.contains("WARN") ? theme.warn : theme.info;
            r.drawText(Views.truncate(r, a, c.width - 6), c.x, y, col, false);
            y += 11;
        }
    }

    private void renderToolsTab(Renderer r, Theme theme) {
        Rect c = contentRect();
        Views.sectionHeader(r, theme, c.x, c.y, "Safety & systems");
        for (ToggleSwitch t : new ToggleSwitch[]{captureToggle, advancedToggle, unlockToggle, delayToggle, netsimToggle}) {
            t.render(r, theme);
        }
        themeBtn.render(r, theme);
        exportJsonBtn.render(r, theme);
        exportCsvBtn.render(r, theme);
        lossInSlider.render(r, theme);
        lossOutSlider.render(r, theme);
        throttleInSlider.render(r, theme);
        throttleOutSlider.render(r, theme);
    }

    private void renderRulesTab(Renderer r, Theme theme) {
        Rect c = contentRect();
        r.drawText("Intercept: " + data.interceptRuleCount() + "   Discard: " + data.discardRuleCount()
                + "   (left-click a rule = toggle, right-click = delete)", c.x, c.y, theme.textMuted, false);
        rulesList.render(r, theme);
        clearInterceptBtn.render(r, theme);
        clearDiscardBtn.render(r, theme);
    }

    private void renderRuleRow(Renderer r, Theme theme, com.packetauditor.ui.RuleView rv, Rect rb,
                               boolean selected, boolean hovered) {
        if (hovered) {
            r.fillRect(rb, theme.buttonHover.scaleAlpha(0.5));
        }
        int ty = rb.y + (rb.height - r.textHeight()) / 2;
        r.fillRect(new Rect(rb.x + 3, rb.y + 3, 6, 6), rv.enabled ? theme.toggleOn : theme.toggleOff);
        UiColor kindColor = rv.kind == com.packetauditor.ui.RuleView.Kind.DELAY ? theme.outbound : theme.accent;
        r.drawText(rv.kind.name(), rb.x + 14, ty, kindColor, false);
        r.drawText(Views.truncate(r, rv.label, rb.width - 60), rb.x + 52, ty, theme.text, false);
    }

    private void renderFooter(Renderer r, Theme theme) {
        int y = bounds.bottom() - FOOTER;
        r.fillRect(new Rect(bounds.x, y, bounds.width, FOOTER), theme.panelAlt.scaleAlpha(theme.opacity));
        r.drawLine(bounds.x, y, bounds.right(), y, theme.border, 1);
        PerformanceManager.ResourceSnapshot res = data.resources();
        String left = compact
                ? "RAM " + Views.humanBytes(res.usedMemoryBytes())
                : "RAM " + Views.humanBytes(res.usedMemoryBytes()) + "/" + Views.humanBytes(res.maxMemoryBytes())
                    + "   backlog " + res.backgroundBacklog() + "   queue " + res.queuePressure();
        r.drawText(left, bounds.x + 8, y + 4, theme.textMuted, false);
        String hint = compact ? "[Insert]" : "[Insert] toggle  \u2022  right-click a packet for actions";
        r.drawText(hint, bounds.right() - r.textWidth(hint) - 8, y + 4, theme.accent, false);
    }

    private int kv(Renderer r, Theme theme, int x, int y, String key, String value) {
        r.drawText(key, x, y, theme.textMuted, false);
        r.drawText(value, x + 92, y, theme.text, false);
        return y + 12;
    }

    private void renderPacketRow(Renderer r, Theme theme, PacketRow row, Rect rb, boolean selected, boolean hovered) {
        if (row.sequence == selectedSeq) {
            r.fillRect(rb, theme.selection);
        } else if (hovered) {
            r.fillRect(rb, theme.buttonHover.scaleAlpha(0.5));
        }
        int tx = rb.x + 4;
        int ty = rb.y + (rb.height - r.textHeight()) / 2;
        r.drawText(row.directionLabel, tx, ty, row.inbound ? theme.inbound : theme.outbound, false);
        r.drawText(Views.truncate(r, row.name, rb.width - (compact ? 60 : 130)), tx + 26, ty, theme.text, false);
        if (!compact) {
            String size = row.size >= 0 ? row.size + "B" : "?";
            r.drawText(size, rb.right() - 70, ty, theme.textMuted, false);
            r.drawText(row.phase, rb.right() - 40, ty, theme.textMuted, false);
        }
        if (row.severity != null) {
            r.fillRect(new Rect(rb.right() - 8, rb.y + 3, 5, 5), theme.severity(row.severity));
        }
    }

    private void renderHeldRow(Renderer r, Theme theme, HeldView h, Rect rb, boolean selected, boolean hovered) {
        if (hovered) {
            r.fillRect(rb, theme.buttonHover.scaleAlpha(0.5));
        }
        int ty = rb.y + (rb.height - r.textHeight()) / 2;
        r.drawText(h.directionLabel, rb.x + 4, ty, h.inbound ? theme.inbound : theme.outbound, false);
        r.drawText(Views.truncate(r, h.name, rb.width - 70), rb.x + 30, ty, theme.text, false);
        r.drawText("#" + h.id + "  " + (h.ageMillis / 1000) + "s", rb.right() - 60, ty, theme.textMuted, false);
    }

    // ---------------- actions ----------------

    private void openActionMenu(PacketRow row) {
        selectedSeq = row.sequence;
        List<ActionMenu.Item> items = new ArrayList<>();
        items.add(new ActionMenu.Item("Inspect", () -> { selectedSeq = row.sequence; setTab(1); }, false));
        items.add(new ActionMenu.Item("Edit\u2026", () -> { selectedSeq = row.sequence; setTab(1); }, false));
        items.add(new ActionMenu.Item("Intercept type", () -> actions.interceptMatchingType(row.sequence), false));
        items.add(new ActionMenu.Item("Delay type", () -> actions.delayMatchingType(row.sequence), false));
        items.add(new ActionMenu.Item("Discard type", () -> actions.discardMatchingType(row.sequence), true));
        actionMenu.show(lastMouseX, lastMouseY, row.name, items);
    }

    private void setTab(int tab) {
        this.activeTab = tab;
    }

    private void doSave() {
        if (selectedSeq < 0) {
            return;
        }
        int applied = 0;
        String lastError = null;
        for (EditorField ef : editorFields) {
            if (!ef.field.value().equals(ef.original)) {
                String err = actions.saveFieldEdit(selectedSeq, ef.path, ef.field.value());
                if (err == null) {
                    applied++;
                    ef.original = ef.field.value();
                } else {
                    lastError = err;
                }
            }
        }
        if (lastError != null) {
            setStatus("Not saved: " + lastError, false);
        } else if (applied > 0) {
            setStatus("Saved " + applied + " change(s).", true);
        } else {
            setStatus("No changes to save.", true);
        }
    }

    private void doPreview() {
        if (selectedSeq < 0) {
            return;
        }
        for (EditorField ef : editorFields) {
            if (!ef.field.value().equals(ef.original)) {
                String err = actions.previewFieldEdit(selectedSeq, ef.path, ef.field.value());
                if (err != null) {
                    setStatus("Would fail: " + ef.path + " \u2014 " + err, false);
                    return;
                }
            }
        }
        setStatus("Looks valid \u2014 safe to save.", true);
    }

    private void doApplyAll() {
        if (selectedSeq < 0) {
            return;
        }
        int count = 0;
        for (EditorField ef : editorFields) {
            if (!ef.field.value().equals(ef.original)) {
                String err = actions.applyEditToAllMatching(selectedSeq, ef.path, ef.field.value());
                if (err == null) {
                    count++;
                }
            }
        }
        setStatus(count > 0 ? "Installed " + count + " auto-rule(s)." : "Change a field first.", count > 0);
    }

    private void beginManual() {
        if (manualX == null) {
            manualX = bounds.x;
            manualY = bounds.y;
            manualW = bounds.width;
            manualH = bounds.height;
        }
    }

    /** Reset any manual move/resize back to automatic (full/compact) layout. */
    public void resetLayout() {
        manualX = manualY = manualW = manualH = null;
    }

    private void setStatus(String msg, boolean ok) {
        this.editorStatus = msg;
    }

    // ---------------- events ----------------

    @Override
    public boolean onEvent(InputEvent event) {
        if (!visible) {
            return false;
        }
        if (event.type == InputEvent.Type.MOUSE_MOVE) {
            lastMouseX = event.x;
            lastMouseY = event.y;
        }
        if (bounds.width > 0) {
            layout();
        }

        // Action menu takes priority while visible.
        if (actionMenu.visible()) {
            if (actionMenu.onEvent(event)) {
                return true;
            }
        }

        // Drag / resize (header moves, bottom-right grip resizes).
        if (event.type == InputEvent.Type.MOUSE_DOWN) {
            if (resizeGrip().contains(event.x, event.y)) {
                beginManual();
                drag = Drag.RESIZE;
                startW = bounds.width; startH = bounds.height;
                startMouseX = event.x; startMouseY = event.y;
                return true;
            }
            if (event.y >= bounds.y && event.y < bounds.y + HEADER && bounds.contains(event.x, event.y)) {
                beginManual();
                drag = Drag.MOVE;
                dragOffX = event.x - bounds.x;
                dragOffY = event.y - bounds.y;
                return true;
            }
        } else if (event.type == InputEvent.Type.MOUSE_MOVE && drag != Drag.NONE) {
            if (drag == Drag.MOVE) {
                manualX = (int) (event.x - dragOffX);
                manualY = (int) (event.y - dragOffY);
            } else {
                manualW = (int) (startW + (event.x - startMouseX));
                manualH = (int) (startH + (event.y - startMouseY));
            }
            return true;
        } else if (event.type == InputEvent.Type.MOUSE_UP && drag != Drag.NONE) {
            drag = Drag.NONE;
            return true;
        }

        // Tab switching.
        if (event.type == InputEvent.Type.MOUSE_DOWN) {
            int tab = tabAt(event.x, event.y);
            if (tab >= 0) {
                activeTab = tab;
                return true;
            }
        }

        // Editor text fields (Inspect tab).
        if (activeTab == 1) {
            for (EditorField ef : editorFields) {
                if (ef.field.visible() && ef.field.onEvent(event)) {
                    return true;
                }
            }
        }

        // Visible tab children (lists, buttons, toggles), top-most first.
        for (int i = children.size() - 1; i >= 0; i--) {
            Component c = children.get(i);
            if (c.visible() && c.onEvent(event)) {
                return true;
            }
        }
        // Consume clicks inside the panel so they don't fall through to the game.
        return event.isMouse() && bounds.contains(event.x, event.y);
    }

    private int tabAt(double mx, double my) {
        int y = bounds.y + HEADER;
        if (my < y || my > y + TABBAR) {
            return -1;
        }
        for (int i = 0; i < TABS.length; i++) {
            if (tabWidth[i] > 0 && mx >= tabStart[i] && mx < tabStart[i] + tabWidth[i]) {
                return i;
            }
        }
        return -1;
    }

    private static final class EditorField {
        final String path;
        String original;
        final TextField field;

        EditorField(String path, String original, TextField field) {
            this.path = path;
            this.original = original;
            this.field = field;
        }
    }
}
