package com.packetauditor.ui.theme;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Holds the available themes (presets + any custom ones) and the currently active
 * theme, with hot-swapping, global opacity override, and change notifications.
 */
public final class ThemeManager {

    private final Map<String, Theme> themes = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Consumer<Theme>> listeners = new CopyOnWriteArrayList<>();
    private volatile Theme current;

    public ThemeManager() {
        themes.putAll(ThemePresets.all());
        current = themes.getOrDefault("Dark", ThemePresets.dark()).copy();
    }

    public Theme current() {
        return current;
    }

    public Set<String> themeNames() {
        return themes.keySet();
    }

    public Theme get(String name) {
        return themes.get(name);
    }

    /** Register/replace a custom theme. */
    public void register(Theme theme) {
        if (theme != null && theme.name != null) {
            themes.put(theme.name, theme);
        }
    }

    /** Switch the active theme by name (a working copy is activated). */
    public boolean setCurrent(String name) {
        Theme t = themes.get(name);
        if (t == null) {
            return false;
        }
        setCurrent(t.copy());
        return true;
    }

    public void setCurrent(Theme theme) {
        if (theme == null) {
            return;
        }
        this.current = theme;
        for (Consumer<Theme> l : listeners) {
            try {
                l.accept(theme);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Override the global opacity of the active theme (0..1). */
    public void setOpacity(double opacity) {
        current.opacity = Math.max(0.0, Math.min(1.0, opacity));
        setCurrent(current);
    }

    public void onThemeChanged(Consumer<Theme> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }
}
