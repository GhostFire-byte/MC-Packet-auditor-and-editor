package com.packetauditor.ui.component;

import com.packetauditor.ui.InputEvent;
import com.packetauditor.ui.Rect;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.theme.Theme;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * A horizontal slider bound to an external numeric value in a {@code [min,max]}
 * range. Reads the current value via a supplier and reports changes via a
 * consumer, so it stays in sync with backend state (used for netsim loss %,
 * throttle rate and delay amounts).
 */
public class Slider extends Component {

    private final String label;
    private final double min;
    private final double max;
    private final DoubleSupplier valueSupplier;
    private final DoubleConsumer onChange;
    private DoubleFunction<String> format = v -> String.format("%.0f", v);
    private boolean dragging = false;

    public Slider(String label, double min, double max, DoubleSupplier valueSupplier, DoubleConsumer onChange) {
        this.label = label;
        this.min = min;
        this.max = Math.max(min + 1e-9, max);
        this.valueSupplier = valueSupplier;
        this.onChange = onChange == null ? v -> { } : onChange;
    }

    public Slider format(DoubleFunction<String> fmt) {
        this.format = fmt == null ? v -> String.valueOf(v) : fmt;
        return this;
    }

    @Override
    public void render(Renderer r, Theme theme) {
        if (!visible) {
            return;
        }
        double value = clamp(valueSupplier.getAsDouble());
        double frac = (value - min) / (max - min);

        int th = r.textHeight();
        int labelY = bounds.y;
        if (label != null && !label.isEmpty()) {
            r.drawText(label, bounds.x, labelY, theme.textMuted, false);
            String v = format.apply(value);
            r.drawText(v, bounds.right() - r.textWidth(v), labelY, theme.text, false);
        }

        int trackY = bounds.y + th + 2;
        int trackH = 4;
        Rect track = new Rect(bounds.x, trackY, bounds.width, trackH);
        r.fillRect(track, theme.toggleOff);
        int fillW = (int) (bounds.width * frac);
        r.fillRect(new Rect(bounds.x, trackY, fillW, trackH), theme.accent);

        int knobX = bounds.x + fillW - 2;
        r.fillRect(new Rect(knobX, trackY - 2, 4, trackH + 4), theme.text);
    }

    @Override
    public boolean onEvent(InputEvent event) {
        super.onEvent(event);
        if (!visible) {
            return false;
        }
        switch (event.type) {
            case MOUSE_DOWN:
                if (hitTrack(event.x, event.y)) {
                    dragging = true;
                    apply(event.x);
                    return true;
                }
                return false;
            case MOUSE_MOVE:
                if (dragging) {
                    apply(event.x);
                    return true;
                }
                return false;
            case MOUSE_UP:
                if (dragging) {
                    dragging = false;
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private boolean hitTrack(double x, double y) {
        int th = 9;
        int trackY = bounds.y + th;
        return x >= bounds.x && x <= bounds.right() && y >= trackY - 2 && y <= trackY + 8;
    }

    private void apply(double mouseX) {
        double frac = (mouseX - bounds.x) / (double) bounds.width;
        frac = Math.max(0, Math.min(1, frac));
        onChange.accept(min + frac * (max - min));
    }

    private double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }
}
