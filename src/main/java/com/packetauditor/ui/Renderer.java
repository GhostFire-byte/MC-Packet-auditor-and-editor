package com.packetauditor.ui;

import com.packetauditor.ui.theme.GlassSpec;

/**
 * The rendering seam (D5). The module's UI model is painted exclusively through
 * this interface, so it never depends on Minecraft or your GUI framework. The
 * bundled {@code src/mc} reference renderer implements this with Minecraft's
 * {@code GuiGraphics}; Codex can supply an alternative binding to your own UI by
 * implementing the same interface — the backend and UI model stay unchanged.
 *
 * <p>Coordinates are in GUI pixels, origin top-left.</p>
 */
public interface Renderer {

    int screenWidth();

    int screenHeight();

    /** Fill a solid rectangle. */
    void fillRect(Rect rect, UiColor color);

    /** Stroke a rectangle border of the given thickness. */
    void drawBorder(Rect rect, UiColor color, int thickness);

    /** Fill a linear gradient (D13). angleDegrees 0=left→right, 90=top→bottom. */
    void fillGradient(Rect rect, UiColor from, UiColor to, double angleDegrees);

    /**
     * Draw a glass-morphism / acrylic background for a region (D13/D14). The
     * renderer should downsample the blur for efficiency and may fall back to a
     * flat translucent fill under load; callers must not assume a full-quality blur.
     */
    void drawGlass(Rect rect, GlassSpec glass);

    /** Draw a single line of text. */
    void drawText(String text, int x, int y, UiColor color, boolean shadow);

    /** Draw a line. */
    void drawLine(int x1, int y1, int x2, int y2, UiColor color, int thickness);

    int textWidth(String text);

    int textHeight();

    /** Push a clip rectangle; subsequent drawing is clipped to it until {@link #popClip()}. */
    void pushClip(Rect rect);

    void popClip();
}
