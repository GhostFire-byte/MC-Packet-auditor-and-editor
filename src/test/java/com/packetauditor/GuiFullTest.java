package com.packetauditor;

import com.packetauditor.api.AuditorConfig;
import com.packetauditor.api.AuditorUiActions;
import com.packetauditor.api.AuditorUiDataSource;
import com.packetauditor.api.PacketAuditor;
import com.packetauditor.model.ProtocolPhase;
import com.packetauditor.ui.InputEvent;
import com.packetauditor.ui.Rect;
import com.packetauditor.ui.component.Slider;
import com.packetauditor.ui.view.DashboardView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiFullTest {

    // Tab centres for a 960x540 full-mode dashboard with the FakeRenderer
    // (textWidth == length*6). Tabs start at x=12 (bounds.x 8 + 4).
    // Packets,Inspect,Queue,Traffic,Anomalies,Rules,Tools
    private static final int[] TAB_CENTERS = {38, 90, 136, 182, 240, 292, 332};
    private static final int TAB_Y = 33;

    private PacketAuditor auditor(Path dir) {
        PacketAuditor a = new PacketAuditor(AuditorConfig.builder().baseDir(dir).enableSqlite(false).build());
        a.registerEditAdapter(new TestFixtures.FakeMoveAdapter());
        a.protection().setAdvancedMode(true);
        a.start();
        a.attach(new FakeTransport());
        for (int i = 0; i < 8; i++) {
            a.handleOutbound(new TestFixtures.FakeMovePacket(i, i, i, true), 1, 12, ProtocolPhase.PLAY, null);
        }
        return a;
    }

    @Test
    void everyTabRendersAndHandlesEventsSafely(@TempDir Path dir) {
        PacketAuditor a = auditor(dir);
        a.changeAllMatching("FakeMovePacket", "y", "0"); // so the Rules tab has content
        var data = new AuditorUiDataSource(a);
        var actions = new AuditorUiActions(a);
        DashboardView dash = new DashboardView(data, actions);
        FakeRenderer r = new FakeRenderer(960, 540);

        dash.render(r, data.theme());

        for (int tab = 0; tab < TAB_CENTERS.length; tab++) {
            dash.onEvent(InputEvent.mouseMove(TAB_CENTERS[tab], TAB_Y));
            dash.onEvent(InputEvent.mouseDown(TAB_CENTERS[tab], TAB_Y, 0));
            dash.onEvent(InputEvent.mouseUp(TAB_CENTERS[tab], TAB_Y, 0));
            dash.render(r, data.theme());
            assertEquals(0, r.clipBalance(), "clip stack must stay balanced on tab " + tab);
        }
        assertTrue(r.draws > 0);
        a.stop();
    }

    @Test
    void dragMovesAndResizeChangesBounds(@TempDir Path dir) {
        PacketAuditor a = auditor(dir);
        var data = new AuditorUiDataSource(a);
        DashboardView dash = new DashboardView(data, new AuditorUiActions(a));
        FakeRenderer r = new FakeRenderer(960, 540);
        dash.render(r, data.theme());
        Rect before = dash.bounds();

        // Drag by the header.
        dash.onEvent(InputEvent.mouseDown(100, 14, 0));
        dash.onEvent(InputEvent.mouseMove(160, 70));
        dash.onEvent(InputEvent.mouseUp(160, 70, 0));
        dash.render(r, data.theme());
        assertNotEquals(before.x, dash.bounds().x, "dragging the header should move the panel");

        // Resize from the grip.
        Rect cur = dash.bounds();
        int gx = cur.right() - 5, gy = cur.bottom() - 5;
        dash.onEvent(InputEvent.mouseDown(gx, gy, 0));
        dash.onEvent(InputEvent.mouseMove(gx - 120, gy - 80));
        dash.onEvent(InputEvent.mouseUp(gx - 120, gy - 80, 0));
        dash.render(r, data.theme());
        assertTrue(dash.bounds().width < cur.width, "resizing should shrink the panel");

        dash.resetLayout();
        dash.render(r, data.theme());
        a.stop();
    }

    @Test
    void sliderReportsValueFromDrag() {
        AtomicReference<Double> got = new AtomicReference<>(-1.0);
        Slider s = new Slider("Loss %", 0, 100, () -> 0, got::set);
        s.setBounds(new Rect(0, 0, 100, 16)); // width 100; track just below the label line
        s.onEvent(InputEvent.mouseDown(50, 11, 0));
        assertEquals(50.0, got.get(), 2.0);
        s.onEvent(InputEvent.mouseMove(100, 11));
        assertEquals(100.0, got.get(), 0.5);
        s.onEvent(InputEvent.mouseUp(100, 11, 0));
    }

    @Test
    void rulesTabActionsToggleAndDelete(@TempDir Path dir) {
        PacketAuditor a = auditor(dir);
        var rule = a.changeAllMatching("FakeMovePacket", "y", "0");
        var data = new AuditorUiDataSource(a);
        var actions = new AuditorUiActions(a);

        assertEquals(1, data.rules().size());
        actions.toggleRule(rule.id(), false);
        assertFalse(a.rules().rules().get(0).enabled());
        actions.deleteRule(rule.id());
        assertEquals(0, data.rules().size());
        a.stop();
    }

    @Test
    void netsimSlidersDriveBackend(@TempDir Path dir) {
        PacketAuditor a = auditor(dir);
        var actions = new AuditorUiActions(a);
        actions.setOutboundLoss(0.25);
        actions.setInboundThrottle(64 * 1024);
        assertEquals(0.25, a.netsim().outboundLossProbability(), 0.001);
        assertEquals(64 * 1024, a.netsim().inboundBytesPerSecond());
        a.stop();
    }
}
