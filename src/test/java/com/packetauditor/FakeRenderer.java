package com.packetauditor;

import com.packetauditor.ui.Rect;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.UiColor;
import com.packetauditor.ui.theme.GlassSpec;

/** Minimal counting {@link Renderer} for headless UI smoke tests (no Minecraft). */
public final class FakeRenderer implements Renderer {

    public int draws = 0;
    private int clipDepth = 0;
    private final int w;
    private final int h;

    public FakeRenderer(int w, int h) {
        this.w = w;
        this.h = h;
    }

    public int clipBalance() {
        return clipDepth;
    }

    @Override public int screenWidth()  { return w; }
    @Override public int screenHeight() { return h; }
    @Override public void fillRect(Rect r, UiColor c) { draws++; }
    @Override public void drawBorder(Rect r, UiColor c, int t) { draws++; }
    @Override public void fillGradient(Rect r, UiColor a, UiColor b, double angle) { draws++; }
    @Override public void drawGlass(Rect r, GlassSpec g) { draws++; }
    @Override public void drawText(String text, int x, int y, UiColor c, boolean shadow) { draws++; }
    @Override public void drawLine(int x1, int y1, int x2, int y2, UiColor c, int t) { draws++; }
    @Override public int textWidth(String text) { return text == null ? 0 : text.length() * 6; }
    @Override public int textHeight() { return 9; }
    @Override public void pushClip(Rect r) { clipDepth++; }
    @Override public void popClip() { clipDepth--; }
}
