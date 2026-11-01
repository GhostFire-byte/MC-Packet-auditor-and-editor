package com.packetauditor.anomaly;

/**
 * An immutable detected anomaly (spec §11). Purely diagnostic: recording an
 * anomaly never alters, blocks, or drops traffic.
 */
public final class Anomaly {

    private final long timestampMillis;
    private final String detectorId;
    private final Severity severity;
    private final String message;
    private final long packetSequence;   // -1 if not tied to a specific packet
    private final String packetName;

    public Anomaly(long timestampMillis, String detectorId, Severity severity, String message,
                   long packetSequence, String packetName) {
        this.timestampMillis = timestampMillis;
        this.detectorId = detectorId;
        this.severity = severity;
        this.message = message;
        this.packetSequence = packetSequence;
        this.packetName = packetName;
    }

    public long timestampMillis() { return timestampMillis; }
    public String detectorId()    { return detectorId; }
    public Severity severity()    { return severity; }
    public String message()       { return message; }
    public long packetSequence()  { return packetSequence; }
    public String packetName()    { return packetName; }

    @Override
    public String toString() {
        return "[" + severity + "] " + detectorId + ": " + message;
    }
}
