package com.packetauditor.ui;

/**
 * A normalised input event fed into the UI model by the renderer binding (D5).
 * The reference MC renderer translates Minecraft mouse/keyboard callbacks into
 * these; an alternative binding does the same with your own input system.
 */
public final class InputEvent {

    public enum Type {
        MOUSE_MOVE,
        MOUSE_DOWN,
        MOUSE_UP,
        SCROLL,
        KEY_DOWN,
        KEY_UP,
        CHAR_TYPED
    }

    public final Type type;
    public final double x;
    public final double y;
    public final int button;     // mouse button (0=left,1=right,2=middle)
    public final int key;        // key code
    public final double scroll;  // scroll delta
    public final int codepoint;  // typed character

    private InputEvent(Type type, double x, double y, int button, int key, double scroll, int codepoint) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.button = button;
        this.key = key;
        this.scroll = scroll;
        this.codepoint = codepoint;
    }

    public static InputEvent mouseMove(double x, double y) {
        return new InputEvent(Type.MOUSE_MOVE, x, y, -1, -1, 0, 0);
    }

    public static InputEvent mouseDown(double x, double y, int button) {
        return new InputEvent(Type.MOUSE_DOWN, x, y, button, -1, 0, 0);
    }

    public static InputEvent mouseUp(double x, double y, int button) {
        return new InputEvent(Type.MOUSE_UP, x, y, button, -1, 0, 0);
    }

    public static InputEvent scroll(double x, double y, double delta) {
        return new InputEvent(Type.SCROLL, x, y, -1, -1, delta, 0);
    }

    public static InputEvent keyDown(int key) {
        return new InputEvent(Type.KEY_DOWN, 0, 0, -1, key, 0, 0);
    }

    public static InputEvent keyUp(int key) {
        return new InputEvent(Type.KEY_UP, 0, 0, -1, key, 0, 0);
    }

    public static InputEvent charTyped(int codepoint) {
        return new InputEvent(Type.CHAR_TYPED, 0, 0, -1, -1, 0, codepoint);
    }

    public boolean isMouse() {
        return type == Type.MOUSE_MOVE || type == Type.MOUSE_DOWN
                || type == Type.MOUSE_UP || type == Type.SCROLL;
    }
}
