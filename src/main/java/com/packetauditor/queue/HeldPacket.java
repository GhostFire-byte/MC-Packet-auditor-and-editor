package com.packetauditor.queue;

import com.packetauditor.model.CapturedPacket;

/**
 * A packet currently held in the interception queue, with its hold time and
 * timeout deadline (0 = no timeout).
 */
public final class HeldPacket {

    private final long id;
    private volatile CapturedPacket packet;   // may be replaced by an edit-before-release
    private final long heldAtMillis;
    private final long timeoutAtMillis;        // 0 == never
    private final String reason;

    public HeldPacket(long id, CapturedPacket packet, long heldAtMillis,
                      long timeoutAtMillis, String reason) {
        this.id = id;
        this.packet = packet;
        this.heldAtMillis = heldAtMillis;
        this.timeoutAtMillis = timeoutAtMillis;
        this.reason = reason == null ? "" : reason;
    }

    public long id()                 { return id; }
    public CapturedPacket packet()   { return packet; }
    public long heldAtMillis()       { return heldAtMillis; }
    public long timeoutAtMillis()    { return timeoutAtMillis; }
    public String reason()           { return reason; }

    public void replacePacket(CapturedPacket edited) {
        if (edited != null) {
            this.packet = edited;
        }
    }

    public boolean isTimedOut(long nowMillis) {
        return timeoutAtMillis > 0 && nowMillis >= timeoutAtMillis;
    }

    public long ageMillis(long nowMillis) {
        return nowMillis - heldAtMillis;
    }

    @Override
    public String toString() {
        return "Held#" + id + " " + packet.meta().packetName()
                + " (" + packet.meta().direction().shortLabel() + ")";
    }
}
