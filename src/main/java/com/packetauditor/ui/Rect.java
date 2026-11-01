package com.packetauditor.ui;

/** Immutable integer rectangle used for component bounds and hit-testing. */
public final class Rect {

    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public Rect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public int right()  { return x + width; }
    public int bottom() { return y + height; }

    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    public Rect inset(int all) {
        return inset(all, all, all, all);
    }

    public Rect inset(int left, int top, int right, int bottom) {
        return new Rect(x + left, y + top, width - left - right, height - top - bottom);
    }

    public Rect translate(int dx, int dy) {
        return new Rect(x + dx, y + dy, width, height);
    }

    public Rect withHeight(int h) {
        return new Rect(x, y, width, h);
    }

    public Rect withWidth(int w) {
        return new Rect(x, y, w, height);
    }

    @Override
    public String toString() {
        return "Rect[" + x + "," + y + " " + width + "x" + height + "]";
    }
}
