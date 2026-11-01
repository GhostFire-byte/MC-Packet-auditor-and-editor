package com.packetauditor.ui.component;

import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.theme.Theme;

/**
 * A container with a themed background (optionally a gradient and/or a
 * glass-morphism acrylic layer) and a border. Used as the surface for the
 * overlay and dashboard panels.
 */
public class Panel extends Container {

    private boolean useGlass = false;
    private boolean useGradient = false;
    private boolean border = true;
    private boolean altSurface = false;

    public Panel glass(boolean v)    { this.useGlass = v; return this; }
    public Panel gradient(boolean v) { this.useGradient = v; return this; }
    public Panel border(boolean v)   { this.border = v; return this; }
    public Panel alt(boolean v)      { this.altSurface = v; return this; }

    @Override
    public void render(Renderer renderer, Theme theme) {
        if (!visible) {
            return;
        }
        if (useGlass && theme.glass.enabled) {
            renderer.drawGlass(bounds, theme.glass);
        } else if (useGradient && theme.gradient.enabled) {
            renderer.fillGradient(bounds, theme.gradient.from, theme.gradient.to,
                    theme.gradient.angleDegrees);
        } else {
            var surface = (altSurface ? theme.panelAlt : theme.panel).scaleAlpha(theme.opacity);
            renderer.fillRect(bounds, surface);
        }
        if (border) {
            renderer.drawBorder(bounds, theme.border, 1);
        }
        super.render(renderer, theme);
    }
}
