package com.packetauditor;

import com.packetauditor.api.AuditorConfig;
import com.packetauditor.api.AuditorUiActions;
import com.packetauditor.api.AuditorUiDataSource;
import com.packetauditor.api.Notification;
import com.packetauditor.api.PacketAuditor;
import com.packetauditor.codec.PacketEditAdapter;
import com.packetauditor.model.ProtocolPhase;
import com.packetauditor.rules.EditResult;
import com.packetauditor.ui.view.DashboardView;
import com.packetauditor.ui.view.OverlayView;
import com.packetauditor.ui.view.ToastOverlay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiAndSafetyTest {

    /** An adapter that always throws from applyEdit, to prove edits can't crash. */
    private static final class ThrowingAdapter implements PacketEditAdapter {
        public boolean supports(Object p) { return p instanceof TestFixtures.FakeMovePacket; }
        public String label() { return "throwing"; }
        public com.packetauditor.model.DecodedPacket decode(Object p) {
            return com.packetauditor.model.DecodedPacket.curated(List.of(
                    com.packetauditor.model.PacketField.scalar("y",
                            com.packetauditor.model.FieldType.DOUBLE, 1.0, true)), true, null);
        }
        public Object applyEdit(Object p, String path, Object v) throws EditException {
            throw new RuntimeException("boom"); // simulate a faulty adapter
        }
    }

    private PacketAuditor newAuditor(Path dir) {
        PacketAuditor a = new PacketAuditor(AuditorConfig.builder().baseDir(dir).enableSqlite(false).build());
        a.start();
        a.attach(new FakeTransport());
        return a;
    }

    @Test
    void faultyAdapterEditNeverThrowsAndNotifies(@TempDir Path dir) {
        PacketAuditor a = newAuditor(dir);
        a.registerEditAdapter(new ThrowingAdapter());
        a.protection().setAdvancedMode(true);

        List<Notification> notes = new ArrayList<>();
        a.addNotificationListener(notes::add);

        // Capture one packet so it's in the live buffer.
        a.handleOutbound(new TestFixtures.FakeMovePacket(1, 2, 3, true), 1, 10, ProtocolPhase.PLAY, null);
        long seq = a.pipeline().totalProcessed();

        EditResult result = a.editPacketField(seq, "y", "99"); // adapter throws internally
        assertFalse(result.applied(), "faulty edit must not report success");
        assertTrue(result.blocked());
        assertFalse(notes.isEmpty(), "a notification should be emitted on save");
        assertEquals(Notification.Level.WARN, notes.get(notes.size() - 1).level());
        a.stop();
    }

    @Test
    void previewDoesNotRecordHistory(@TempDir Path dir) {
        PacketAuditor a = newAuditor(dir);
        a.registerEditAdapter(new TestFixtures.FakeMoveAdapter());
        a.protection().setAdvancedMode(true);
        a.handleOutbound(new TestFixtures.FakeMovePacket(1, 2, 3, true), 1, 10, ProtocolPhase.PLAY, null);
        long seq = a.pipeline().totalProcessed();

        EditResult preview = a.previewPacketEdit(seq, "y", "0");
        assertTrue(preview.applied(), "valid edit should preview as applicable");
        a.stop();
    }

    @Test
    void dashboardAndOverlayRenderWithoutThrowing(@TempDir Path dir) {
        PacketAuditor a = newAuditor(dir);
        a.registerEditAdapter(new TestFixtures.FakeMoveAdapter());
        // Feed a few packets so the list has content.
        for (int i = 0; i < 5; i++) {
            a.handleOutbound(new TestFixtures.FakeMovePacket(i, i, i, true), 1, 12, ProtocolPhase.PLAY, null);
        }
        AuditorUiDataSource data = new AuditorUiDataSource(a);
        AuditorUiActions actions = new AuditorUiActions(a);

        FakeRenderer r = new FakeRenderer(960, 540);

        DashboardView dash = new DashboardView(data, actions);
        dash.render(r, data.theme());

        OverlayView overlay = new OverlayView(data).anchorTopLeft(4, 4);
        overlay.render(r, data.theme());

        ToastOverlay toast = new ToastOverlay();
        toast.push("SUCCESS", "Change saved", "x -> 0");
        toast.render(r, data.theme());

        assertTrue(r.draws > 0, "views should have drawn something");
        assertEquals(0, r.clipBalance(), "every pushClip must be matched by popClip");
        a.stop();
    }

    @Test
    void togglesDriveBackendThroughActions(@TempDir Path dir) {
        PacketAuditor a = newAuditor(dir);
        AuditorUiActions actions = new AuditorUiActions(a);
        actions.setAdvancedMode(true);
        assertTrue(a.protection().isAdvancedMode());
        actions.setUnlockAllProtected(true);
        assertTrue(a.protection().isUnlockAllProtected());
        actions.setCaptureEnabled(false);
        assertFalse(a.pipeline().captureEnabled());
        a.stop();
    }
}
