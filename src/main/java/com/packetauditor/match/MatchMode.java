package com.packetauditor.match;

/**
 * The three configurable matching modes (spec §7). Higher modes are supersets of
 * lower ones. Switching modes never deletes rules; rules that require a richer
 * mode than the current one are simply marked inactive.
 */
public enum MatchMode {

    /** Match by packet type (name/id), direction and phase only. */
    BASIC,

    /** BASIC plus AND-combined field conditions. */
    INTERMEDIATE,

    /** Full rule builder: AND/OR, size, priorities, all properties. */
    ADVANCED;

    public boolean satisfies(MatchMode required) {
        return this.ordinal() >= required.ordinal();
    }
}
