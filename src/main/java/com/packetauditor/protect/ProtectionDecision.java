package com.packetauditor.protect;

/**
 * Immutable result of a protection check: whether the action is allowed, and a
 * clear, user-facing reason when it is blocked (shown in the UI per spec §10).
 */
public final class ProtectionDecision {

    private final boolean allowed;
    private final ProtectionTier tier;
    private final String reason;

    private ProtectionDecision(boolean allowed, ProtectionTier tier, String reason) {
        this.allowed = allowed;
        this.tier = tier;
        this.reason = reason;
    }

    public boolean allowed()       { return allowed; }
    public boolean blocked()       { return !allowed; }
    public ProtectionTier tier()   { return tier; }

    /** Non-null human-readable explanation (empty when allowed without caveat). */
    public String reason()         { return reason == null ? "" : reason; }

    public static ProtectionDecision allow(ProtectionTier tier) {
        return new ProtectionDecision(true, tier, "");
    }

    public static ProtectionDecision allow(ProtectionTier tier, String note) {
        return new ProtectionDecision(true, tier, note);
    }

    public static ProtectionDecision block(ProtectionTier tier, String reason) {
        return new ProtectionDecision(false, tier, reason);
    }

    @Override
    public String toString() {
        return (allowed ? "ALLOW" : "BLOCK") + "[" + tier + "] " + reason();
    }
}
