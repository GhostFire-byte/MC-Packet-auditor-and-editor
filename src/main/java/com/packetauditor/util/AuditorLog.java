package com.packetauditor.util;

import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Consumer;

/**
 * Minimal, dependency-free logger so the core never couples to Minecraft's or
 * the host's logging framework. The host may redirect output via
 * {@link #setSink(Consumer)} (e.g. into its own logger).
 */
public final class AuditorLog {

    public enum Level { TRACE, DEBUG, INFO, WARN, ERROR }

    private static volatile Level minLevel = Level.INFO;
    private static volatile Consumer<String> sink = System.out::println;
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final String tag;

    private AuditorLog(String tag) {
        this.tag = tag;
    }

    public static AuditorLog of(Class<?> owner) {
        return new AuditorLog(owner.getSimpleName());
    }

    public static AuditorLog of(String tag) {
        return new AuditorLog(tag);
    }

    public static void setMinLevel(Level level) {
        minLevel = level == null ? Level.INFO : level;
    }

    public static void setSink(Consumer<String> newSink) {
        sink = newSink == null ? System.out::println : newSink;
    }

    public void trace(String msg)            { log(Level.TRACE, msg, null); }
    public void debug(String msg)            { log(Level.DEBUG, msg, null); }
    public void info(String msg)             { log(Level.INFO, msg, null); }
    public void warn(String msg)             { log(Level.WARN, msg, null); }
    public void warn(String msg, Throwable t){ log(Level.WARN, msg, t); }
    public void error(String msg)            { log(Level.ERROR, msg, null); }
    public void error(String msg, Throwable t){ log(Level.ERROR, msg, t); }

    private void log(Level level, String msg, Throwable t) {
        if (level.ordinal() < minLevel.ordinal()) {
            return;
        }
        String line = TS.format(Instant.now()) + " [" + level + "] [" + tag + "] " + msg;
        sink.accept(line);
        if (t != null) {
            sink.accept("    " + t);
            for (StackTraceElement e : t.getStackTrace()) {
                sink.accept("        at " + e);
            }
        }
    }
}
