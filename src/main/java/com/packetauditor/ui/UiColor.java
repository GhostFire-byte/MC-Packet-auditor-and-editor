package com.packetauditor.ui;

/**
 * Immutable packed ARGB colour (0xAARRGGBB) with convenience helpers. Pure Java;
 * renderers translate this to their own colour representation.
 */
public final class UiColor {

    public final int argb;

    public UiColor(int argb) {
        this.argb = argb;
    }

    public static UiColor rgba(int r, int g, int b, int a) {
        return new UiColor(((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF));
    }

    public static UiColor rgb(int r, int g, int b) {
        return rgba(r, g, b, 255);
    }

    public static UiColor hex(int rgb) {
        return new UiColor(0xFF000000 | (rgb & 0xFFFFFF));
    }

    public int alpha() { return (argb >>> 24) & 0xFF; }
    public int red()   { return (argb >>> 16) & 0xFF; }
    public int green() { return (argb >>> 8) & 0xFF; }
    public int blue()  { return argb & 0xFF; }

    /** Return this colour with a new alpha (0..255). */
    public UiColor withAlpha(int a) {
        return rgba(red(), green(), blue(), a);
    }

    /** Scale alpha by a 0..1 opacity factor. */
    public UiColor scaleAlpha(double opacity) {
        int a = (int) Math.round(alpha() * Math.max(0, Math.min(1, opacity)));
        return withAlpha(a);
    }

    /** Linear interpolation between this and {@code other} by t in [0,1]. */
    public UiColor lerp(UiColor other, double t) {
        double u = Math.max(0, Math.min(1, t));
        return rgba(
                (int) Math.round(red() + (other.red() - red()) * u),
                (int) Math.round(green() + (other.green() - green()) * u),
                (int) Math.round(blue() + (other.blue() - blue()) * u),
                (int) Math.round(alpha() + (other.alpha() - alpha()) * u));
    }

    @Override
    public String toString() {
        return String.format("#%08X", argb);
    }
}
