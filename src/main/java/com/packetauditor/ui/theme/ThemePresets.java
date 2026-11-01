package com.packetauditor.ui.theme;

import com.packetauditor.ui.UiColor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in theme presets (spec §14 + D13): Dark, Light, Cyberpunk, Monochrome,
 * Night, Lavender, Toxic-Green, Deep-Ocean.
 */
public final class ThemePresets {

    private ThemePresets() {
    }

    public static Map<String, Theme> all() {
        Map<String, Theme> map = new LinkedHashMap<>();
        for (Theme t : new Theme[]{premium(), dark(), light(), cyberpunk(), monochrome(), night(),
                lavender(), toxicGreen(), deepOcean()}) {
            map.put(t.name, t);
        }
        return map;
    }

    /**
     * High-contrast, polished "Premium" theme (default for the bundled GUI):
     * near-white text on deep charcoal, a refined warm accent, subtle gradient and
     * acrylic. Chosen for readability (no low-contrast dark-on-dark text).
     */
    public static Theme premium() {
        Theme t = new Theme();
        t.name = "Premium";
        t.background = UiColor.hex(0x0C0D12);
        t.panel = UiColor.hex(0x15161D);
        t.panelAlt = UiColor.hex(0x1E202A);
        t.border = UiColor.hex(0x2E3242);
        t.text = UiColor.hex(0xF6F7FB);          // near-white, strong contrast
        t.textMuted = UiColor.hex(0xB4BAC8);     // light grey, still readable
        t.accent = UiColor.hex(0xE8B55C);        // refined warm amber (premium, not pure gold)
        t.accentMuted = UiColor.hex(0x8A6A2E);
        t.selection = UiColor.rgba(232, 181, 92, 48);
        t.inbound = UiColor.hex(0x6BE08F);       // bright green, high contrast
        t.outbound = UiColor.hex(0xF2A65A);      // warm orange
        t.info = UiColor.hex(0x6FB4FF);
        t.warn = UiColor.hex(0xF2C14E);
        t.critical = UiColor.hex(0xFF6B6B);
        t.buttonBg = UiColor.hex(0x20222D);
        t.buttonHover = UiColor.hex(0x2B2E3B);
        t.toggleOn = UiColor.hex(0xE8B55C);
        t.toggleOff = UiColor.hex(0x3A3E4C);
        t.protectedTint = UiColor.rgba(255, 107, 107, 40);
        t.opacity = 0.96;
        t.gradient = new GradientSpec(true, UiColor.hex(0x0C0D12), UiColor.hex(0x15161D), 90);
        t.glass = new GlassSpec(true, 8, UiColor.rgba(232, 181, 92, 18), 0.16);
        return t;
    }

    public static Theme dark() {
        return new Theme(); // defaults are already a dark theme
    }

    public static Theme light() {
        Theme t = new Theme();
        t.name = "Light";
        t.background = UiColor.hex(0xF4F5F7);
        t.panel = UiColor.hex(0xFFFFFF);
        t.panelAlt = UiColor.hex(0xEDEEF2);
        t.border = UiColor.hex(0xD3D6DE);
        t.text = UiColor.hex(0x1A1A20);
        t.textMuted = UiColor.hex(0x6A6A76);
        t.accent = UiColor.hex(0x2F80ED);
        t.accentMuted = UiColor.hex(0xAACCF5);
        t.buttonBg = UiColor.hex(0xE7E9EF);
        t.buttonHover = UiColor.hex(0xDADDE6);
        t.toggleOff = UiColor.hex(0xBFC2CC);
        t.opacity = 0.97;
        return t;
    }

    public static Theme cyberpunk() {
        Theme t = new Theme();
        t.name = "Cyberpunk";
        t.background = UiColor.hex(0x0B0014);
        t.panel = UiColor.hex(0x15012B);
        t.panelAlt = UiColor.hex(0x1F0440);
        t.border = UiColor.hex(0xFF2D95);
        t.text = UiColor.hex(0xF6E7FF);
        t.textMuted = UiColor.hex(0xB06CD9);
        t.accent = UiColor.hex(0x00F0FF);
        t.accentMuted = UiColor.hex(0x007A99);
        t.inbound = UiColor.hex(0x00FFC6);
        t.outbound = UiColor.hex(0xFF2D95);
        t.info = UiColor.hex(0x00F0FF);
        t.warn = UiColor.hex(0xFFD400);
        t.critical = UiColor.hex(0xFF3860);
        t.toggleOn = UiColor.hex(0x00F0FF);
        t.gradient = new GradientSpec(true, UiColor.hex(0x15012B), UiColor.hex(0x3A006A), 120);
        t.glass = new GlassSpec(true, 10, UiColor.rgba(255, 45, 149, 30), 0.22);
        return t;
    }

    public static Theme monochrome() {
        Theme t = new Theme();
        t.name = "Monochrome";
        t.background = UiColor.hex(0x121212);
        t.panel = UiColor.hex(0x1C1C1C);
        t.panelAlt = UiColor.hex(0x262626);
        t.border = UiColor.hex(0x3A3A3A);
        t.text = UiColor.hex(0xEDEDED);
        t.textMuted = UiColor.hex(0x8A8A8A);
        t.accent = UiColor.hex(0xBFBFBF);
        t.accentMuted = UiColor.hex(0x595959);
        t.inbound = UiColor.hex(0xBFBFBF);
        t.outbound = UiColor.hex(0x8A8A8A);
        t.info = UiColor.hex(0xBFBFBF);
        t.warn = UiColor.hex(0xDADADA);
        t.critical = UiColor.hex(0xFFFFFF);
        t.toggleOn = UiColor.hex(0xBFBFBF);
        return t;
    }

    public static Theme night() {
        Theme t = new Theme();
        t.name = "Night";
        t.background = UiColor.hex(0x070B16);
        t.panel = UiColor.hex(0x0E1526);
        t.panelAlt = UiColor.hex(0x152034);
        t.border = UiColor.hex(0x223049);
        t.text = UiColor.hex(0xDCE6F5);
        t.textMuted = UiColor.hex(0x6E7E9A);
        t.accent = UiColor.hex(0x3D6BE5);
        t.accentMuted = UiColor.hex(0x25406F);
        t.gradient = new GradientSpec(true, UiColor.hex(0x070B16), UiColor.hex(0x121D33), 90);
        return t;
    }

    public static Theme lavender() {
        Theme t = new Theme();
        t.name = "Lavender";
        t.background = UiColor.hex(0x1A1626);
        t.panel = UiColor.hex(0x241E36);
        t.panelAlt = UiColor.hex(0x2F2746);
        t.border = UiColor.hex(0x4A3E6E);
        t.text = UiColor.hex(0xEDE7FA);
        t.textMuted = UiColor.hex(0xA99BD1);
        t.accent = UiColor.hex(0xB48CFF);
        t.accentMuted = UiColor.hex(0x6A54A0);
        t.inbound = UiColor.hex(0x9DE1C0);
        t.outbound = UiColor.hex(0xF4A9D0);
        t.toggleOn = UiColor.hex(0xB48CFF);
        t.glass = new GlassSpec(true, 8, UiColor.rgba(180, 140, 255, 30), 0.2);
        return t;
    }

    public static Theme toxicGreen() {
        Theme t = new Theme();
        t.name = "Toxic-Green";
        t.background = UiColor.hex(0x071003);
        t.panel = UiColor.hex(0x0D1A06);
        t.panelAlt = UiColor.hex(0x14280A);
        t.border = UiColor.hex(0x3DA200);
        t.text = UiColor.hex(0xEAFFD6);
        t.textMuted = UiColor.hex(0x86B85E);
        t.accent = UiColor.hex(0x8CFF00);
        t.accentMuted = UiColor.hex(0x3DA200);
        t.inbound = UiColor.hex(0x8CFF00);
        t.outbound = UiColor.hex(0xC6FF59);
        t.warn = UiColor.hex(0xE5FF00);
        t.critical = UiColor.hex(0xFF7A00);
        t.toggleOn = UiColor.hex(0x8CFF00);
        return t;
    }

    public static Theme deepOcean() {
        Theme t = new Theme();
        t.name = "Deep-Ocean";
        t.background = UiColor.hex(0x02121A);
        t.panel = UiColor.hex(0x05202E);
        t.panelAlt = UiColor.hex(0x083244);
        t.border = UiColor.hex(0x0E5A73);
        t.text = UiColor.hex(0xDFF4FA);
        t.textMuted = UiColor.hex(0x6FA6B8);
        t.accent = UiColor.hex(0x1CC8D6);
        t.accentMuted = UiColor.hex(0x0E6E78);
        t.inbound = UiColor.hex(0x37E0C8);
        t.outbound = UiColor.hex(0x4FA8FF);
        t.gradient = new GradientSpec(true, UiColor.hex(0x02121A), UiColor.hex(0x063447), 110);
        t.glass = new GlassSpec(true, 9, UiColor.rgba(28, 200, 214, 26), 0.2);
        return t;
    }
}
