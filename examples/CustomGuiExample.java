// Illustrative: render the module's premium dashboard with YOUR OWN GUI by
// implementing com.packetauditor.ui.Renderer. Nothing here imports Minecraft —
// this is exactly how you drop the UI into your own rendering stack.
//
// Replace the `MyGpu` calls with your renderer's primitives.

import com.packetauditor.api.*;
import com.packetauditor.ui.*;
import com.packetauditor.ui.theme.GlassSpec;
import com.packetauditor.ui.view.DashboardView;
import com.packetauditor.ui.view.OverlayView;
import com.packetauditor.ui.view.ToastOverlay;

public final class CustomGuiExample {

    /** Your renderer: map the module's primitives onto your own GUI's draw calls. */
    static final class MyGuiRenderer implements Renderer {
        private final MyGpu gpu;              // <- your own immediate-mode draw API
        MyGuiRenderer(MyGpu gpu) { this.gpu = gpu; }

        public int screenWidth()  { return gpu.width(); }
        public int screenHeight() { return gpu.height(); }
        public void fillRect(Rect r, UiColor c) { gpu.rect(r.x, r.y, r.width, r.height, c.argb); }
        public void drawBorder(Rect r, UiColor c, int t) { gpu.rectOutline(r.x, r.y, r.width, r.height, t, c.argb); }
        public void fillGradient(Rect r, UiColor a, UiColor b, double angle) { gpu.gradient(r.x, r.y, r.width, r.height, a.argb, b.argb, angle); }
        public void drawGlass(Rect r, GlassSpec g) { gpu.blurPanel(r.x, r.y, r.width, r.height, g.blurRadius, g.tint.argb, g.tintOpacity); }
        public void drawText(String s, int x, int y, UiColor c, boolean shadow) { gpu.text(s, x, y, c.argb, shadow); }
        public void drawLine(int x1, int y1, int x2, int y2, UiColor c, int t) { gpu.line(x1, y1, x2, y2, t, c.argb); }
        public int textWidth(String s) { return gpu.measure(s); }
        public int textHeight() { return gpu.lineHeight(); }
        public void pushClip(Rect r) { gpu.pushScissor(r.x, r.y, r.width, r.height); }
        public void popClip() { gpu.popScissor(); }
    }

    static void wire(PacketAuditor auditor, MyGpu gpu) {
        // Read-side + write-side adapters bind the views to the SAME backend.
        AuditorUiDataSource data = new AuditorUiDataSource(auditor);
        AuditorUiActions actions = new AuditorUiActions(auditor,
                seq -> myShowDetails(seq),          // left-click
                seq -> myOpenActionMenu(seq));      // right-click -> your menu

        DashboardView dashboard = new DashboardView(data, actions);
        OverlayView overlay = new OverlayView(data).anchorTopLeft(4, 4);
        ToastOverlay toasts = new ToastOverlay();
        auditor.addNotificationListener(n -> toasts.push(n.level().name(), n.title(), n.message()));

        MyGuiRenderer renderer = new MyGuiRenderer(gpu);

        // Per frame (in your render loop):
        if (auditor.isOverlayVisible()) dashboard.render(renderer, data.theme());
        else                            overlay.render(renderer, data.theme());
        toasts.render(renderer, data.theme());

        // Per input event (from your input system):
        // dashboard.onEvent(InputEvent.mouseDown(mx, my, button));
        // if (key == auditor.toggleKey()) auditor.toggleOverlay();

        // Save an edit (emits a toast via the notification listener above):
        // auditor.editPacketField(sequence, "y", "0");
        // Preview first to warn before saving:
        // EditResult preview = auditor.previewPacketEdit(sequence, "y", "0");
    }

    // --- placeholders for your engine ---
    interface MyGpu {
        int width(); int height(); int measure(String s); int lineHeight();
        void rect(int x,int y,int w,int h,int argb);
        void rectOutline(int x,int y,int w,int h,int t,int argb);
        void gradient(int x,int y,int w,int h,int a,int b,double angle);
        void blurPanel(int x,int y,int w,int h,int blur,int tint,double op);
        void text(String s,int x,int y,int argb,boolean shadow);
        void line(int x1,int y1,int x2,int y2,int t,int argb);
        void pushScissor(int x,int y,int w,int h); void popScissor();
    }
    static void myShowDetails(long seq) { }
    static void myOpenActionMenu(long seq) { }
}
