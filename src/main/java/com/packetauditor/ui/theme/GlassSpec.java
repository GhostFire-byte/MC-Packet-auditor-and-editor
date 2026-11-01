package com.packetauditor.ui.theme;

import com.packetauditor.ui.UiColor;

/**
 * Glass-morphism (acrylic) layer spec (D13/D14): a translucent, blurred panel
 * background. Rendering is adaptive and must fall back to flat translucency under
 * load; {@code blurRadius} is a hint the renderer may downscale for efficiency.
 */
public final class GlassSpec {

    public boolean enabled;
    public int blurRadius;        // hint; renderer may downsample
    public UiColor tint;
    public double tintOpacity;    // 0..1

    public GlassSpec() {
        this(false, 8, UiColor.rgba(255, 255, 255, 40), 0.25);
    }

    public GlassSpec(boolean enabled, int blurRadius, UiColor tint, double tintOpacity) {
        this.enabled = enabled;
        this.blurRadius = Math.max(0, blurRadius);
        this.tint = tint;
        this.tintOpacity = Math.max(0, Math.min(1, tintOpacity));
    }

    public GlassSpec copy() {
        return new GlassSpec(enabled, blurRadius, tint, tintOpacity);
    }
}
