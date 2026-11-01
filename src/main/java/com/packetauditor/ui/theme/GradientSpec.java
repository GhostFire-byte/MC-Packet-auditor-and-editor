package com.packetauditor.ui.theme;

import com.packetauditor.ui.UiColor;

/** Optional linear-gradient background spec for a theme (D13). */
public final class GradientSpec {

    public boolean enabled;
    public UiColor from;
    public UiColor to;
    public double angleDegrees;

    public GradientSpec() {
        this(false, UiColor.hex(0x101018), UiColor.hex(0x202030), 90);
    }

    public GradientSpec(boolean enabled, UiColor from, UiColor to, double angleDegrees) {
        this.enabled = enabled;
        this.from = from;
        this.to = to;
        this.angleDegrees = angleDegrees;
    }

    public GradientSpec copy() {
        return new GradientSpec(enabled, from, to, angleDegrees);
    }
}
