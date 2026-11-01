package com.packetauditor.ui;

/** Render-friendly view of a packet currently held in the interception queue. */
public final class HeldView {

    public final long id;
    public final String name;
    public final String directionLabel;
    public final boolean inbound;
    public final long ageMillis;

    public HeldView(long id, String name, String directionLabel, boolean inbound, long ageMillis) {
        this.id = id;
        this.name = name;
        this.directionLabel = directionLabel;
        this.inbound = inbound;
        this.ageMillis = ageMillis;
    }
}
