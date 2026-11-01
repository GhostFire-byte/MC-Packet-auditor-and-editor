package com.packetauditor.ui.view;

import com.packetauditor.ui.Rect;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.theme.Theme;

/** Small shared drawing helpers for the bundled views (premium, consistent look). */
final class Views {

    private Views() {
    }

    /** Draw a themed panel surface (glass / gradient / flat) with a soft border. */
    static void panelBackground(Renderer r, Theme theme, Rect bounds, boolean elevated) {
        if (theme.glass.enabled) {
            r.drawGlass(bounds, theme.glass);
        } else if (theme.gradient.enabled) {
            r.fillGradient(bounds, theme.gradient.from, theme.gradient.to, theme.gradient.angleDegrees);
        } else {
            r.fillRect(bounds, (elevated ? theme.panel : theme.panelAlt).scaleAlpha(theme.opacity));
        }
        r.drawBorder(bounds, theme.border, 1);
    }

    /** Draw a section header (muted label with an accent tick). */
    static void sectionHeader(Renderer r, Theme theme, int x, int y, String label) {
        r.fillRect(new Rect(x, y + 1, 2, r.textHeight() - 1), theme.accent);
        r.drawText(label.toUpperCase(), x + 6, y, theme.textMuted, false);
    }

    /** Truncate text with an ellipsis to fit a pixel width. */
    static String truncate(Renderer r, String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (r.textWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "\u2026";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (r.textWidth(sb.toString() + text.charAt(i) + ellipsis) > maxWidth) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb.append(ellipsis).toString();
    }

    /** Human-readable byte size. */
    static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        return String.format("%.1f GB", mb / 1024.0);
    }
}
