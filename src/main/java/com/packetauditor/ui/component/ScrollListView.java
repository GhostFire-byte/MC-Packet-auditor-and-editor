package com.packetauditor.ui.component;

import com.packetauditor.ui.InputEvent;
import com.packetauditor.ui.Rect;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.theme.Theme;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A vertically scrolling list of items with selection and hover (the persistent
 * packet list in spec §14). Generic and reusable: supply an item source and a
 * row renderer. Live-updates by re-reading the supplier each frame.
 */
public class ScrollListView<T> extends Component {

    /** Renders one row. */
    public interface RowRenderer<T> {
        void render(Renderer renderer, Theme theme, T item, Rect row, boolean selected, boolean hovered);
    }

    private final Supplier<List<T>> itemsSupplier;
    private final RowRenderer<T> rowRenderer;
    private int rowHeight = 14;
    private double scrollOffset = 0;
    private int selectedIndex = -1;
    private int hoveredIndex = -1;
    private boolean stickToBottom = true;
    private Consumer<T> onSelect = t -> { };
    private Consumer<T> onActivate = t -> { };  // right-click / double-use -> action menu

    public ScrollListView(Supplier<List<T>> itemsSupplier, RowRenderer<T> rowRenderer) {
        this.itemsSupplier = itemsSupplier;
        this.rowRenderer = rowRenderer;
    }

    public ScrollListView<T> rowHeight(int h) {
        this.rowHeight = Math.max(6, h);
        return this;
    }

    public ScrollListView<T> onSelect(Consumer<T> cb) {
        this.onSelect = cb == null ? t -> { } : cb;
        return this;
    }

    public ScrollListView<T> onActivate(Consumer<T> cb) {
        this.onActivate = cb == null ? t -> { } : cb;
        return this;
    }

    public void setStickToBottom(boolean stick) {
        this.stickToBottom = stick;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    @Override
    public void render(Renderer renderer, Theme theme) {
        if (!visible) {
            return;
        }
        List<T> items = itemsSupplier.get();
        int total = items.size();
        int contentHeight = total * rowHeight;
        int viewHeight = bounds.height;

        double maxScroll = Math.max(0, contentHeight - viewHeight);
        if (stickToBottom) {
            scrollOffset = maxScroll;
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        renderer.pushClip(bounds);
        int first = (int) (scrollOffset / rowHeight);
        int y = bounds.y - (int) (scrollOffset % rowHeight);
        for (int i = first; i < total && y < bounds.bottom(); i++, y += rowHeight) {
            Rect row = new Rect(bounds.x, y, bounds.width, rowHeight);
            rowRenderer.render(renderer, theme, items.get(i), row, i == selectedIndex, i == hoveredIndex);
        }
        renderer.popClip();
    }

    @Override
    public boolean onEvent(InputEvent event) {
        super.onEvent(event);
        if (!visible || !containsMouse(event)) {
            if (event.type == InputEvent.Type.MOUSE_MOVE) {
                hoveredIndex = -1;
            }
            return false;
        }
        switch (event.type) {
            case SCROLL:
                stickToBottom = false;
                scrollOffset -= event.scroll * rowHeight * 3;
                return true;
            case MOUSE_MOVE:
                hoveredIndex = indexAt(event.y);
                return false;
            case MOUSE_DOWN: {
                int idx = indexAt(event.y);
                List<T> items = itemsSupplier.get();
                if (idx >= 0 && idx < items.size()) {
                    selectedIndex = idx;
                    if (event.button == 1) {
                        onActivate.accept(items.get(idx)); // right-click opens the action menu
                    } else {
                        onSelect.accept(items.get(idx));
                    }
                    return true;
                }
                return false;
            }
            default:
                return false;
        }
    }

    private int indexAt(double mouseY) {
        int rel = (int) ((mouseY - bounds.y) + scrollOffset);
        if (rel < 0) {
            return -1;
        }
        return rel / rowHeight;
    }
}
