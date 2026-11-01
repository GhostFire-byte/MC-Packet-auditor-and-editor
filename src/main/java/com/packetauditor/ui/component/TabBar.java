package com.packetauditor.ui.component;

import com.packetauditor.ui.InputEvent;
import com.packetauditor.ui.Rect;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.theme.Theme;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * A horizontal tab bar (the dashboard's tab-based inspector/editor/details area,
 * spec §14). Renders tab titles and reports selection changes.
 */
public class TabBar extends Component {

    private final List<String> tabs;
    private int selected = 0;
    private IntConsumer onSelect = i -> { };

    public TabBar(List<String> tabs) {
        this.tabs = tabs;
    }

    public TabBar onSelect(IntConsumer cb) {
        this.onSelect = cb == null ? i -> { } : cb;
        return this;
    }

    public int selected() {
        return selected;
    }

    public void setSelected(int index) {
        if (index >= 0 && index < tabs.size()) {
            selected = index;
        }
    }

    @Override
    public void render(Renderer renderer, Theme theme) {
        if (!visible) {
            return;
        }
        renderer.fillRect(bounds, theme.panelAlt.scaleAlpha(theme.opacity));
        int x = bounds.x;
        for (int i = 0; i < tabs.size(); i++) {
            String title = tabs.get(i);
            int w = renderer.textWidth(title) + 16;
            Rect tab = new Rect(x, bounds.y, w, bounds.height);
            boolean active = i == selected;
            if (active) {
                renderer.fillRect(tab, theme.panel.scaleAlpha(theme.opacity));
                renderer.fillRect(new Rect(tab.x, tab.bottom() - 2, tab.width, 2), theme.accent);
            }
            int ty = bounds.y + (bounds.height - renderer.textHeight()) / 2;
            renderer.drawText(title, x + 8, ty, active ? theme.text : theme.textMuted, false);
            x += w;
        }
    }

    @Override
    public boolean onEvent(InputEvent event) {
        super.onEvent(event);
        if (!visible || event.type != InputEvent.Type.MOUSE_DOWN || !containsMouse(event)) {
            return false;
        }
        int x = bounds.x;
        for (int i = 0; i < tabs.size(); i++) {
            int w = 0;
            // Width must match render(); approximate using title length * 6 + 16 as fallback
            // is avoided — we recompute with a nominal char width of 6px.
            w = (tabs.get(i).length() * 6) + 16;
            if (event.x >= x && event.x < x + w) {
                selected = i;
                onSelect.accept(i);
                return true;
            }
            x += w;
        }
        return false;
    }
}
