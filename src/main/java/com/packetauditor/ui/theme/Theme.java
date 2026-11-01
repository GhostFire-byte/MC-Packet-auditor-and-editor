package com.packetauditor.ui.theme;

import com.packetauditor.ui.UiColor;

/**
 * A colour/appearance theme (spec §14). Holds semantic colour roles plus global
 * opacity, an optional {@link GradientSpec} background and an optional
 * {@link GlassSpec} acrylic layer. Fields are intentionally flat and simple so
 * theme JSON stays lightweight (D13).
 */
public final class Theme {

    public String name = "Dark";

    // Core surfaces
    public UiColor background = UiColor.hex(0x0E0E12);
    public UiColor panel      = UiColor.hex(0x16161C);
    public UiColor panelAlt   = UiColor.hex(0x1E1E26);
    public UiColor border     = UiColor.hex(0x2A2A34);

    // Text
    public UiColor text       = UiColor.hex(0xECECEC);
    public UiColor textMuted  = UiColor.hex(0x9A9AA6);

    // Accents
    public UiColor accent      = UiColor.hex(0x4F9DFF);
    public UiColor accentMuted = UiColor.hex(0x2C5A91);
    public UiColor selection   = UiColor.rgba(79, 157, 255, 60);

    // Direction colours
    public UiColor inbound  = UiColor.hex(0x56C271);   // S->C
    public UiColor outbound = UiColor.hex(0xFFB454);   // C->S

    // Severity colours (anomalies)
    public UiColor info     = UiColor.hex(0x4F9DFF);
    public UiColor warn     = UiColor.hex(0xFFB454);
    public UiColor critical = UiColor.hex(0xFF5D5D);

    // Controls
    public UiColor buttonBg    = UiColor.hex(0x22222C);
    public UiColor buttonHover = UiColor.hex(0x2E2E3A);
    public UiColor toggleOn     = UiColor.hex(0x56C271);
    public UiColor toggleOff    = UiColor.hex(0x4A4A56);

    // Protected-packet highlight
    public UiColor protectedTint = UiColor.rgba(255, 93, 93, 40);

    // Global appearance
    public double opacity = 0.95;
    public GradientSpec gradient = new GradientSpec();
    public GlassSpec glass = new GlassSpec();

    public Theme() {
    }

    public UiColor severity(com.packetauditor.anomaly.Severity sev) {
        if (sev == null) {
            return textMuted;
        }
        switch (sev) {
            case CRITICAL: return critical;
            case WARN:     return warn;
            default:       return info;
        }
    }

    public UiColor direction(com.packetauditor.model.Direction d) {
        return d == com.packetauditor.model.Direction.INBOUND ? inbound : outbound;
    }

    /** Deep copy so edits to a working theme don't mutate a preset. */
    public Theme copy() {
        Theme t = new Theme();
        t.name = name;
        t.background = background; t.panel = panel; t.panelAlt = panelAlt; t.border = border;
        t.text = text; t.textMuted = textMuted;
        t.accent = accent; t.accentMuted = accentMuted; t.selection = selection;
        t.inbound = inbound; t.outbound = outbound;
        t.info = info; t.warn = warn; t.critical = critical;
        t.buttonBg = buttonBg; t.buttonHover = buttonHover;
        t.toggleOn = toggleOn; t.toggleOff = toggleOff;
        t.protectedTint = protectedTint;
        t.opacity = opacity;
        t.gradient = gradient.copy();
        t.glass = glass.copy();
        return t;
    }
}
