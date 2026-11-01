package com.packetauditor.ui.view;

import com.packetauditor.ui.Rect;
import com.packetauditor.ui.Renderer;
import com.packetauditor.ui.UiColor;
import com.packetauditor.ui.component.Component;
import com.packetauditor.ui.theme.Theme;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Renders transient notification toasts (e.g. the "Change saved" / "Change not
 * applied" messages emitted when editing packets). Decoupled from the API layer:
 * the host pushes toasts, typically by subscribing a
 * {@code PacketAuditor.addNotificationListener} that calls {@link #push}.
 *
 * <p>Toasts fade out and auto-expire; rendering is bounded and cheap.</p>
 */
public final class ToastOverlay extends Component {

    public enum Level { INFO, SUCCESS, WARN, ERROR }

    private static final long LIFETIME_MILLIS = 4_000;
    private static final long FADE_MILLIS = 600;
    private static final int MAX_VISIBLE = 5;
    private static final int WIDTH = 230;

    private final Deque<Toast> toasts = new ArrayDeque<>();
    private final Object lock = new Object();

    public void push(String level, String title, String message) {
        Level lvl;
        try {
            lvl = Level.valueOf(level == null ? "INFO" : level.toUpperCase());
        } catch (IllegalArgumentException e) {
            lvl = Level.INFO;
        }
        synchronized (lock) {
            toasts.addLast(new Toast(lvl, title, message, System.currentTimeMillis()));
            while (toasts.size() > 32) {
                toasts.pollFirst();
            }
        }
    }

    @Override
    public void render(Renderer r, Theme theme) {
        if (!visible) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Toast> live = new ArrayList<>();
        synchronized (lock) {
            toasts.removeIf(t -> now - t.createdAt > LIFETIME_MILLIS);
            int from = Math.max(0, toasts.size() - MAX_VISIBLE);
            int i = 0;
            for (Toast t : toasts) {
                if (i++ >= from) {
                    live.add(t);
                }
            }
        }

        int x = r.screenWidth() - WIDTH - 8;
        int y = 8;
        for (Toast t : live) {
            double alpha = 1.0;
            long age = now - t.createdAt;
            long remaining = LIFETIME_MILLIS - age;
            if (remaining < FADE_MILLIS) {
                alpha = Math.max(0, remaining / (double) FADE_MILLIS);
            }
            int lines = (t.message == null || t.message.isEmpty()) ? 1 : 2;
            int height = 8 + lines * 11;
            Rect box = new Rect(x, y, WIDTH, height);

            UiColor accent = color(theme, t.level);
            r.fillRect(box, theme.panel.scaleAlpha(0.92 * alpha));
            r.drawBorder(box, theme.border.scaleAlpha(alpha), 1);
            r.fillRect(new Rect(box.x, box.y, 3, box.height), accent.scaleAlpha(alpha));

            r.drawText(Views.truncate(r, t.title, WIDTH - 16), box.x + 9, box.y + 4,
                    theme.text.scaleAlpha(alpha), false);
            if (lines == 2) {
                r.drawText(Views.truncate(r, t.message, WIDTH - 16), box.x + 9, box.y + 15,
                        theme.textMuted.scaleAlpha(alpha), false);
            }
            y += height + 4;
        }
    }

    private UiColor color(Theme theme, Level level) {
        switch (level) {
            case ERROR:   return theme.critical;
            case WARN:    return theme.warn;
            case SUCCESS: return theme.inbound;
            default:      return theme.info;
        }
    }

    private static final class Toast {
        final Level level;
        final String title;
        final String message;
        final long createdAt;

        Toast(Level level, String title, String message, long createdAt) {
            this.level = level;
            this.title = title == null ? "" : title;
            this.message = message == null ? "" : message;
            this.createdAt = createdAt;
        }
    }
}
