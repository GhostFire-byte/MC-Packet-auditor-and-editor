package com.packetauditor.ui.component;

import com.packetauditor.ui.InputEvent;
import com.packetauditor.ui.Rect;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.UiColor;
import com.packetauditor.ui.theme.Theme;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * An on/off toggle switch bound to an external boolean state (spec §10 UI: the
 * protected-packet list shows a toggle switch). Reads state via a supplier and
 * reports changes via a consumer, so it stays in sync with backend flags.
 */
public class ToggleSwitch extends Component {

    private final String label;
    private final BooleanSupplier stateSupplier;
    private final Consumer<Boolean> onToggle;

    public ToggleSwitch(String label, BooleanSupplier stateSupplier, Consumer<Boolean> onToggle) {
        this.label = label;
        this.stateSupplier = stateSupplier;
        this.onToggle = onToggle == null ? v -> { } : onToggle;
    }

    @Override
    public void render(Renderer renderer, Theme theme) {
        if (!visible) {
            return;
        }
        boolean on = stateSupplier.getAsBoolean();

        // Label on the left.
        int th = renderer.textHeight();
        int ty = bounds.y + (bounds.height - th) / 2;
        if (label != null && !label.isEmpty()) {
            renderer.drawText(label, bounds.x, ty, theme.text, false);
        }

        // Track + knob on the right.
        int trackW = 26;
        int trackH = 12;
        int trackX = bounds.right() - trackW;
        int trackY = bounds.y + (bounds.height - trackH) / 2;
        Rect track = new Rect(trackX, trackY, trackW, trackH);
        UiColor trackColor = on ? theme.toggleOn : theme.toggleOff;
        renderer.fillRect(track, trackColor);

        int knob = trackH - 2;
        int knobX = on ? track.right() - knob - 1 : track.x + 1;
        renderer.fillRect(new Rect(knobX, trackY + 1, knob, knob), theme.text);
    }

    @Override
    public boolean onEvent(InputEvent event) {
        super.onEvent(event);
        if (!visible) {
            return false;
        }
        if (event.type == InputEvent.Type.MOUSE_DOWN && event.button == 0 && containsMouse(event)) {
            boolean next = !stateSupplier.getAsBoolean();
            onToggle.accept(next);
            return true;
        }
        return false;
    }
}
