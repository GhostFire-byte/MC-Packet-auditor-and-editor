package com.packetauditor.ui.component;

import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.UiColor;
import com.packetauditor.ui.theme.Theme;

import java.util.function.Function;
import java.util.function.Supplier;

/** A single line of text. Supports a static string or a dynamic supplier. */
public class Label extends Component {

    public enum Align { LEFT, CENTER, RIGHT }

    private Supplier<String> textSupplier;
    private Function<Theme, UiColor> colorFn = t -> t.text;
    private Align align = Align.LEFT;
    private boolean shadow = true;

    public Label(String text) {
        this.textSupplier = () -> text;
    }

    public Label(Supplier<String> textSupplier) {
        this.textSupplier = textSupplier;
    }

    public Label color(Function<Theme, UiColor> fn) {
        this.colorFn = fn;
        return this;
    }

    public Label align(Align align) {
        this.align = align;
        return this;
    }

    public Label shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public void setText(String text) {
        this.textSupplier = () -> text;
    }

    @Override
    public void render(Renderer renderer, Theme theme) {
        if (!visible) {
            return;
        }
        String text = textSupplier.get();
        if (text == null) {
            return;
        }
        int tw = renderer.textWidth(text);
        int x;
        switch (align) {
            case CENTER: x = bounds.x + (bounds.width - tw) / 2; break;
            case RIGHT:  x = bounds.right() - tw; break;
            default:     x = bounds.x; break;
        }
        int y = bounds.y + (bounds.height - renderer.textHeight()) / 2;
        renderer.drawText(text, x, y, colorFn.apply(theme), shadow);
    }
}
