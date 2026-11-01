package com.packetauditor.ui.component;

import com.packetauditor.ui.InputEvent;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.theme.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * A component that holds and lays out child components. Events are dispatched to
 * children top-most first; rendering is back-to-front.
 */
public class Container extends Component {

    protected final List<Component> children = new ArrayList<>();

    public void add(Component child) {
        if (child != null) {
            children.add(child);
        }
    }

    public void clear() {
        children.clear();
    }

    public List<Component> children() {
        return children;
    }

    @Override
    public void render(Renderer renderer, Theme theme) {
        if (!visible) {
            return;
        }
        for (Component c : children) {
            if (c.visible()) {
                c.render(renderer, theme);
            }
        }
    }

    @Override
    public boolean onEvent(InputEvent event) {
        super.onEvent(event);
        // Dispatch to children in reverse (top-most) order.
        for (int i = children.size() - 1; i >= 0; i--) {
            Component c = children.get(i);
            if (c.visible() && c.onEvent(event)) {
                return true;
            }
        }
        return false;
    }
}
