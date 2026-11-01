package com.packetauditor.ui.component;

import com.packetauditor.ui.InputEvent;
import com.packetauditor.ui.Rect;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.theme.Theme;

import java.util.function.Supplier;

/** A clickable button with a label and hover state. */
public class Button extends Component {

    private Supplier<String> label;
    private Runnable onClick = () -> { };
    private boolean enabled = true;

    public Button(String label, Runnable onClick) {
        this.label = () -> label;
        this.onClick = onClick == null ? () -> { } : onClick;
    }

    public Button(Supplier<String> label, Runnable onClick) {
        this.label = label;
        this.onClick = onClick == null ? () -> { } : onClick;
    }

    public Button enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public void setLabel(String text) {
        this.label = () -> text;
    }

    @Override
    public void render(Renderer renderer, Theme theme) {
        if (!visible) {
            return;
        }
        var bg = !enabled ? theme.buttonBg.scaleAlpha(0.5)
                : (hovered ? theme.buttonHover : theme.buttonBg);
        renderer.fillRect(bounds, bg);
        renderer.drawBorder(bounds, theme.border, 1);
        String text = label.get();
        int tw = renderer.textWidth(text);
        int x = bounds.x + (bounds.width - tw) / 2;
        int y = bounds.y + (bounds.height - renderer.textHeight()) / 2;
        renderer.drawText(text, x, y, enabled ? theme.text : theme.textMuted, false);
    }

    @Override
    public boolean onEvent(InputEvent event) {
        super.onEvent(event);
        if (!enabled || !visible) {
            return false;
        }
        if (event.type == InputEvent.Type.MOUSE_DOWN && event.button == 0
                && containsMouse(event)) {
            onClick.run();
            return true;
        }
        return false;
    }

    /** Convenience for laying this button out. */
    public Button at(Rect r) {
        setBounds(r);
        return this;
    }
}
