package com.packetauditor.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of applying one or more edits to a packet: the resulting native packet
 * (possibly unchanged), whether anything was applied, a blocked reason (e.g. from
 * the protection layer), and the list of {@link ModificationRecord}s produced.
 */
public final class EditResult {

    private final boolean applied;
    private final boolean blocked;
    private final String message;
    private final Object resultingNativePacket;
    private final List<ModificationRecord> modifications;

    private EditResult(boolean applied, boolean blocked, String message,
                       Object resultingNativePacket, List<ModificationRecord> modifications) {
        this.applied = applied;
        this.blocked = blocked;
        this.message = message == null ? "" : message;
        this.resultingNativePacket = resultingNativePacket;
        this.modifications = Collections.unmodifiableList(
                new ArrayList<>(modifications == null ? List.of() : modifications));
    }

    public boolean applied()                       { return applied; }
    public boolean blocked()                       { return blocked; }
    public String message()                        { return message; }
    public Object resultingNativePacket()          { return resultingNativePacket; }
    public List<ModificationRecord> modifications(){ return modifications; }

    public static EditResult unchanged(Object nativePacket, String message) {
        return new EditResult(false, false, message, nativePacket, List.of());
    }

    public static EditResult blocked(Object nativePacket, String reason) {
        return new EditResult(false, true, reason, nativePacket, List.of());
    }

    public static EditResult applied(Object nativePacket, List<ModificationRecord> mods) {
        return new EditResult(true, false, "Applied " + mods.size() + " change(s)",
                nativePacket, mods);
    }
}
