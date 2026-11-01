package com.packetauditor.mc.render;

import com.packetauditor.api.AuditorUiActions;
import com.packetauditor.api.AuditorUiDataSource;
import com.packetauditor.api.PacketAuditor;
import com.packetauditor.ui.InputEvent;
import com.packetauditor.ui.view.DashboardView;
import com.packetauditor.ui.view.OverlayView;
import com.packetauditor.ui.view.ToastOverlay;

import net.minecraft.client.gui.GuiGraphics;

import java.util.function.LongConsumer;

/**
 * Convenience host that wires the bundled views to the Minecraft reference
 * renderer and the auditor backend. Render it from your HUD/overlay pass so it
 * draws over the game (and the inventory). The full dashboard shows when the
 * overlay is toggled on (Insert); otherwise a compact overlay is drawn. Toasts
 * are always shown.
 *
 * <p>You are free to ignore this host and render {@link DashboardView} /
 * {@link OverlayView} yourself with your own {@link com.packetauditor.ui.Renderer}.</p>
 */
public final class McGuiHost {

    private final PacketAuditor auditor;
    private final McRenderer renderer = new McRenderer();
    private final AuditorUiDataSource data;
    private final DashboardView dashboard;
    private final OverlayView overlay;
    private final ToastOverlay toasts = new ToastOverlay();

    public McGuiHost(PacketAuditor auditor) {
        this(auditor, seq -> { }, seq -> { });
    }

    public McGuiHost(PacketAuditor auditor, LongConsumer onSelect, LongConsumer onActionMenu) {
        this.auditor = auditor;
        this.data = new AuditorUiDataSource(auditor);
        AuditorUiActions actions = new AuditorUiActions(auditor, onSelect, onActionMenu);
        this.dashboard = new DashboardView(data, actions);
        this.overlay = new OverlayView(data).anchorTopLeft(4, 4);
        // Route backend notifications into toasts (e.g. save-change messages).
        auditor.addNotificationListener(n -> toasts.push(n.level().name(), n.title(), n.message()));
    }

    /** Render the UI for the current frame. Call from your HUD/overlay render. */
    public void render(GuiGraphics graphics) {
        renderer.begin(graphics);
        if (auditor.isOverlayVisible()) {
            // Compact docked layout when a Minecraft screen (inventory/container) is open,
            // so the dashboard stays small and does not cover the whole screen.
            dashboard.setCompact(net.minecraft.client.Minecraft.getInstance().screen != null);
            dashboard.render(renderer, data.theme());
        } else {
            overlay.render(renderer, data.theme());
        }
        toasts.render(renderer, data.theme());
    }

    // ----- input (forward from your input callbacks) -----

    public boolean mouseMoved(double x, double y) {
        return dispatch(InputEvent.mouseMove(x, y));
    }

    public boolean mouseClicked(double x, double y, int button) {
        return dispatch(InputEvent.mouseDown(x, y, button));
    }

    public boolean mouseReleased(double x, double y, int button) {
        return dispatch(InputEvent.mouseUp(x, y, button));
    }

    public boolean mouseScrolled(double x, double y, double delta) {
        return dispatch(InputEvent.scroll(x, y, delta));
    }

    public boolean keyPressed(int key) {
        if (key == auditor.toggleKey()) {
            auditor.toggleOverlay();
            return true;
        }
        return dispatch(InputEvent.keyDown(key));
    }

    public boolean charTyped(int codepoint) {
        return dispatch(InputEvent.charTyped(codepoint));
    }

    private boolean dispatch(InputEvent e) {
        if (!auditor.isOverlayVisible()) {
            return false; // compact overlay is non-interactive
        }
        return dashboard.onEvent(e);
    }

    public ToastOverlay toasts() {
        return toasts;
    }

    public DashboardView dashboard() {
        return dashboard;
    }

    public OverlayView overlay() {
        return overlay;
    }
}
