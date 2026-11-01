package com.packetauditor.ui.component;

import com.packetauditor.ui.InputEvent;
import com.packetauditor.ui.Rect;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.theme.Theme;

/**
 * Base class for all UI components. Framework-free: a component knows its bounds,
 * paints itself through the {@link Renderer}, and optionally consumes
 * {@link InputEvent}s. No Minecraft or host-GUI types are referenced.
 */
public abstract class Component {

    protected Rect bounds = new Rect(0, 0, 0, 0);
    protected boolean visible = true;
    protected boolean hovered = false;

    public Rect bounds() {
        return bounds;
    }

    public void setBounds(Rect bounds) {
        this.bounds = bounds == null ? new Rect(0, 0, 0, 0) : bounds;
    }

    public boolean visible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /** Paint this component. */
    public abstract void render(Renderer renderer, Theme theme);

    /**
     * Handle an input event. Return true if consumed (stops further dispatch).
     * Default implementation tracks hover for mouse-move events.
     */
    public boolean onEvent(InputEvent event) {
        if (event.type == InputEvent.Type.MOUSE_MOVE) {
            hovered = bounds.contains(event.x, event.y);
        }
        return false;
    }

    protected boolean containsMouse(InputEvent event) {
        return bounds.contains(event.x, event.y);
    }
}
