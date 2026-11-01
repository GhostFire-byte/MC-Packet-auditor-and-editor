package com.packetauditor.ui.view;

import com.packetauditor.ui.InputEvent;
import com.packetauditor.ui.Rect;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.component.Component;
import com.packetauditor.ui.theme.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * A small popup action menu (spec §6): shown on right-click of a packet with
 * actions like Inspect / Edit / Change all / Intercept / Delay / Discard. Closes
 * on selection or an outside click. Hidden (uncluttered) until invoked.
 */
public final class ActionMenu extends Component {

    public static final class Item {
        final String label;
        final Runnable action;
        final boolean danger;

        public Item(String label, Runnable action, boolean danger) {
            this.label = label;
            this.action = action;
            this.danger = danger;
        }
    }

    private static final int ROW = 13;
    private static final int WIDTH = 132;

    private final List<Item> items = new ArrayList<>();
    private String title = "";
    private int hoverIndex = -1;

    public ActionMenu() {
        setVisible(false); // hidden until explicitly shown (must not intercept input)
    }

    public void show(double x, double y, String title, List<Item> menuItems) {
        this.items.clear();
        this.items.addAll(menuItems);
        this.title = title == null ? "" : title;
        int height = 14 + items.size() * ROW + 3;
        setBounds(new Rect((int) x, (int) y, WIDTH, height));
        setVisible(true);
    }

    public void hide() {
        setVisible(false);
        items.clear();
    }

    @Override
    public void render(Renderer r, Theme theme) {
        if (!visible || items.isEmpty()) {
            return;
        }
        // Keep on-screen.
        int x = Math.min(bounds.x, r.screenWidth() - bounds.width - 2);
        int y = Math.min(bounds.y, r.screenHeight() - bounds.height - 2);
        setBounds(new Rect(Math.max(2, x), Math.max(2, y), bounds.width, bounds.height));

        Views.panelBackground(r, theme, bounds, true);
        r.fillRect(new Rect(bounds.x, bounds.y, bounds.width, 2), theme.accent);
        r.drawText(Views.truncate(r, title, WIDTH - 8), bounds.x + 5, bounds.y + 3, theme.accent, false);

        int yy = bounds.y + 14;
        for (int i = 0; i < items.size(); i++) {
            Rect row = new Rect(bounds.x, yy, bounds.width, ROW);
            if (i == hoverIndex) {
                r.fillRect(row, theme.buttonHover.scaleAlpha(0.7));
            }
            Item it = items.get(i);
            var color = it.danger ? theme.critical : theme.text;
            r.drawText(it.label, bounds.x + 7, yy + 2, color, false);
            yy += ROW;
        }
    }

    @Override
    public boolean onEvent(InputEvent event) {
        if (!visible) {
            return false;
        }
        if (event.type == InputEvent.Type.MOUSE_MOVE) {
            hoverIndex = rowAt(event.x, event.y);
            return false;
        }
        if (event.type == InputEvent.Type.MOUSE_DOWN) {
            int idx = rowAt(event.x, event.y);
            if (idx >= 0 && idx < items.size()) {
                Runnable a = items.get(idx).action;
                hide();
                if (a != null) {
                    a.run();
                }
                return true;
            }
            // Click outside closes the menu (and consumes so it doesn't hit widgets behind).
            hide();
            return true;
        }
        return false;
    }

    private int rowAt(double x, double y) {
        if (!bounds.contains(x, y)) {
            return -1;
        }
        int rel = (int) (y - (bounds.y + 14));
        if (rel < 0) {
            return -1;
        }
        return rel / ROW;
    }
}
