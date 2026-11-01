package com.packetauditor.api.event;

/** An immutable connection-monitoring event (spec §4). */
public final class ConnectionEvent {

    private final ConnectionEventType type;
    private final long timestampMillis;
    private final String detail;
    private final long numericValue; // e.g. latency ms, or -1

    public ConnectionEvent(ConnectionEventType type, String detail, long numericValue) {
        this.type = type;
        this.timestampMillis = System.currentTimeMillis();
        this.detail = detail == null ? "" : detail;
        this.numericValue = numericValue;
    }

    public ConnectionEventType type() { return type; }
    public long timestampMillis()      { return timestampMillis; }
    public String detail()             { return detail; }
    public long numericValue()         { return numericValue; }

    public static ConnectionEvent of(ConnectionEventType type, String detail) {
        return new ConnectionEvent(type, detail, -1);
    }

    @Override
    public String toString() {
        return type + (detail.isEmpty() ? "" : " (" + detail + ")");
    }
}
