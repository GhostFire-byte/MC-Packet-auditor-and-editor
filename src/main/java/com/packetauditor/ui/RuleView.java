package com.packetauditor.ui;

/** Render-friendly view of an active rule for the Rules management tab. */
public final class RuleView {

    public enum Kind { EDIT, DELAY }

    public final String id;
    public final Kind kind;
    public final String label;
    public final boolean enabled;

    public RuleView(String id, Kind kind, String label, boolean enabled) {
        this.id = id;
        this.kind = kind;
        this.label = label;
        this.enabled = enabled;
    }
}
