package com.packetauditor.mc.render;

import com.packetauditor.ui.Rect;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.UiColor;
import com.packetauditor.ui.theme.GlassSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The bundled reference {@link Renderer} (D5), painting the framework-free UI
 * model with Minecraft 26.2's {@code GuiGraphics}. Set the current
 * {@link GuiGraphics} each frame via {@link #begin(GuiGraphics)} before rendering
 * the UI tree.
 *
 * <p>Glass-morphism is implemented as an adaptive translucent fill + tint; a true
 * blur would use a post-processing shader, but the flat-translucency fallback is
 * cheap and always available (D14). Codex can upgrade this to a downsampled blur
 * if desired without touching the UI model.</p>
 */
public final class McRenderer implements Renderer {

    private final Font font;
    private GuiGraphics g;

    public McRenderer() {
        this.font = Minecraft.getInstance().font;
    }

    /** Bind the GuiGraphics for the current frame. */
    public void begin(GuiGraphics graphics) {
        this.g = graphics;
    }

    @Override
    public int screenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    @Override
    public int screenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    @Override
    public void fillRect(Rect r, UiColor color) {
        g.fill(r.x, r.y, r.right(), r.bottom(), color.argb);
    }

    @Override
    public void drawBorder(Rect r, UiColor color, int t) {
        g.fill(r.x, r.y, r.right(), r.y + t, color.argb);                 // top
        g.fill(r.x, r.bottom() - t, r.right(), r.bottom(), color.argb);   // bottom
        g.fill(r.x, r.y, r.x + t, r.bottom(), color.argb);               // left
        g.fill(r.right() - t, r.y, r.right(), r.bottom(), color.argb);    // right
    }

    @Override
    public void fillGradient(Rect r, UiColor from, UiColor to, double angleDegrees) {
        // GuiGraphics supports a vertical gradient; use it as the primary direction.
        g.fillGradient(r.x, r.y, r.right(), r.bottom(), from.argb, to.argb);
    }

    @Override
    public void drawGlass(Rect r, GlassSpec glass) {
        // Adaptive fallback: translucent base + tint (true blur = optional shader).
        UiColor base = UiColor.rgba(10, 10, 14, 150);
        g.fill(r.x, r.y, r.right(), r.bottom(), base.argb);
        if (glass.tint != null) {
            g.fill(r.x, r.y, r.right(), r.bottom(),
                    glass.tint.scaleAlpha(glass.tintOpacity).argb);
        }
    }

    @Override
    public void drawText(String text, int x, int y, UiColor color, boolean shadow) {
        g.drawString(font, text, x, y, color.argb, shadow);
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2, UiColor color, int thickness) {
        // Axis-aligned lines via fill; diagonal lines approximated as a thin box.
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int maxX = Math.max(x1, x2) + (x1 == x2 ? thickness : 0);
        int maxY = Math.max(y1, y2) + (y1 == y2 ? thickness : 0);
        g.fill(minX, minY, Math.max(maxX, minX + thickness), Math.max(maxY, minY + thickness),
                color.argb);
    }

    @Override
    public int textWidth(String text) {
        return font.width(text);
    }

    @Override
    public int textHeight() {
        return font.lineHeight;
    }

    @Override
    public void pushClip(Rect r) {
        g.enableScissor(r.x, r.y, r.right(), r.bottom());
    }

    @Override
    public void popClip() {
        g.disableScissor();
    }
}
