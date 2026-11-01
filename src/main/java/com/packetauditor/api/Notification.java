package com.packetauditor.api;

/**
 * A user-facing notification emitted by the module (e.g. when saving a packet
 * edit). Your GUI can subscribe via {@link PacketAuditor#addNotificationListener}
 * and render these as toasts. Purely informational — the module never blocks on a
 * notification.
 */
public final class Notification {

    public enum Level { INFO, SUCCESS, WARN, ERROR }

    private final Level level;
    private final String title;
    private final String message;
    private final long timestampMillis;

    public Notification(Level level, String title, String message) {
        this.level = level == null ? Level.INFO : level;
        this.title = title == null ? "" : title;
        this.message = message == null ? "" : message;
        this.timestampMillis = System.currentTimeMillis();
    }

    public Level level()            { return level; }
    public String title()           { return title; }
    public String message()         { return message; }
    public long timestampMillis()   { return timestampMillis; }

    public static Notification info(String title, String message) {
        return new Notification(Level.INFO, title, message);
    }

    public static Notification success(String title, String message) {
        return new Notification(Level.SUCCESS, title, message);
    }

    public static Notification warn(String title, String message) {
        return new Notification(Level.WARN, title, message);
    }

    public static Notification error(String title, String message) {
        return new Notification(Level.ERROR, title, message);
    }

    @Override
    public String toString() {
        return "[" + level + "] " + title + (message.isEmpty() ? "" : " \u2014 " + message);
    }

    /** Listener for module notifications. */
    @FunctionalInterface
    public interface Listener {
        void onNotification(Notification notification);
    }
}
