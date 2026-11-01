package com.packetauditor.model;

/**
 * Direction of travel for a packet, relative to the local Minecraft client.
 *
 * <ul>
 *   <li>{@link #INBOUND} — SERVER &rarr; CLIENT (received by this client)</li>
 *   <li>{@link #OUTBOUND} — CLIENT &rarr; SERVER (sent by this client)</li>
 * </ul>
 */
public enum Direction {

    /** SERVER &rarr; CLIENT. */
    INBOUND("SERVER \u2192 CLIENT", "S\u2192C"),

    /** CLIENT &rarr; SERVER. */
    OUTBOUND("CLIENT \u2192 SERVER", "C\u2192S");

    private final String displayName;
    private final String shortLabel;

    Direction(String displayName, String shortLabel) {
        this.displayName = displayName;
        this.shortLabel = shortLabel;
    }

    /** Human-readable name, e.g. {@code "SERVER \u2192 CLIENT"}. */
    public String displayName() {
        return displayName;
    }

    /** Compact label, e.g. {@code "S\u2192C"}. */
    public String shortLabel() {
        return shortLabel;
    }

    public boolean isInbound() {
        return this == INBOUND;
    }

    public boolean isOutbound() {
        return this == OUTBOUND;
    }

    /** The opposite direction. */
    public Direction opposite() {
        return this == INBOUND ? OUTBOUND : INBOUND;
    }
}
