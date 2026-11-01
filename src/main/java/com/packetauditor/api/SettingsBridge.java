package com.packetauditor.api;

/**
 * Optional bridge so the module's settings can be backed by your existing
 * configuration system (e.g. {@code Settings.java}) instead of the module's own
 * JSON store (D12). All methods have sensible defaults, so implementing it is
 * entirely optional; when absent, the module is fully self-contained.
 */
public interface SettingsBridge {

    default String getString(String key, String defaultValue) {
        return defaultValue;
    }

    default void setString(String key, String value) {
    }

    default boolean getBoolean(String key, boolean defaultValue) {
        return defaultValue;
    }

    default void setBoolean(String key, boolean value) {
    }

    default int getInt(String key, int defaultValue) {
        return defaultValue;
    }

    default void setInt(String key, int value) {
    }

    default double getDouble(String key, double defaultValue) {
        return defaultValue;
    }

    default void setDouble(String key, double value) {
    }
}
