package com.packetauditor.ui.component;

import com.packetauditor.ui.InputEvent;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.theme.Theme;

import java.util.function.Consumer;

/**
 * A single-line editable text field with focus, caret, typing, backspace and
 * Enter-to-submit. Framework-free; driven entirely by {@link InputEvent}s so it
 * works with any renderer binding.
 */
public class TextField extends Component {

    // GLFW key codes
    private static final int KEY_ENTER = 257;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_LEFT = 263;
    private static final int KEY_RIGHT = 262;

    private final StringBuilder text = new StringBuilder();
    private String placeholder = "";
    private boolean focused = false;
    private int caret = 0;
    private Consumer<String> onChange = s -> { };
    private Consumer<String> onSubmit = s -> { };

    public TextField() {
    }

    public TextField(String initial) {
        setText(initial);
    }

    public TextField placeholder(String p) {
        this.placeholder = p == null ? "" : p;
        return this;
    }

    public TextField onChange(Consumer<String> cb) {
        this.onChange = cb == null ? s -> { } : cb;
        return this;
    }

    public TextField onSubmit(Consumer<String> cb) {
        this.onSubmit = cb == null ? s -> { } : cb;
        return this;
    }

    public String value() {
        return text.toString();
    }

    public final void setText(String s) {
        text.setLength(0);
        if (s != null) {
            text.append(s);
        }
        caret = text.length();
    }

    public boolean focused() {
        return focused;
    }

    public void setFocused(boolean f) {
        this.focused = f;
    }

    @Override
    public void render(Renderer r, Theme theme) {
        if (!visible) {
            return;
        }
        r.fillRect(bounds, theme.panelAlt.scaleAlpha(theme.opacity));
        r.drawBorder(bounds, focused ? theme.accent : theme.border, 1);
        int ty = bounds.y + (bounds.height - r.textHeight()) / 2;
        int tx = bounds.x + 4;
        String shown = text.length() == 0 && !focused ? placeholder : text.toString();
        var color = text.length() == 0 && !focused ? theme.textMuted : theme.text;
        r.drawText(shown, tx, ty, color, false);
        if (focused) {
            int caretX = tx + r.textWidth(text.substring(0, Math.min(caret, text.length())));
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                r.fillRect(new com.packetauditor.ui.Rect(caretX, ty, 1, r.textHeight()), theme.accent);
            }
        }
    }

    @Override
    public boolean onEvent(InputEvent event) {
        super.onEvent(event);
        if (!visible) {
            return false;
        }
        switch (event.type) {
            case MOUSE_DOWN:
                focused = containsMouse(event);
                return focused;
            case CHAR_TYPED:
                if (focused && event.codepoint >= 32) {
                    text.insert(Math.min(caret, text.length()), (char) event.codepoint);
                    caret++;
                    onChange.accept(text.toString());
                    return true;
                }
                return false;
            case KEY_DOWN:
                if (!focused) {
                    return false;
                }
                return handleKey(event.key);
            default:
                return false;
        }
    }

    private boolean handleKey(int key) {
        switch (key) {
            case KEY_BACKSPACE:
                if (caret > 0 && text.length() > 0) {
                    text.deleteCharAt(caret - 1);
                    caret--;
                    onChange.accept(text.toString());
                }
                return true;
            case KEY_DELETE:
                if (caret < text.length()) {
                    text.deleteCharAt(caret);
                    onChange.accept(text.toString());
                }
                return true;
            case KEY_LEFT:
                if (caret > 0) {
                    caret--;
                }
                return true;
            case KEY_RIGHT:
                if (caret < text.length()) {
                    caret++;
                }
                return true;
            case KEY_ENTER:
                onSubmit.accept(text.toString());
                return true;
            default:
                return true; // consume keys while focused
        }
    }
}
