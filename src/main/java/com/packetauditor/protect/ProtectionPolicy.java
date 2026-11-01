package com.packetauditor.protect;

import com.packetauditor.model.PacketMeta;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central, thread-safe enforcement point for packet protection (spec §10).
 *
 * <p>Two independent controls govern what may be done to a packet:</p>
 * <ol>
 *   <li><b>Advanced Mode</b> — when off, the module is view-only for mutation /
 *       control actions on <i>any</i> packet (safe default). When on, Tier-2
 *       (non-essential) packets may be edited, held, delayed, discarded, etc.</li>
 *   <li><b>Unlock ALL</b> — the explicit experiment toggle (default off,
 *       confirmation-gated in the UI). When on, even Tier-1 essential packets
 *       (KeepAlive/Pong/login/...) may be altered, delayed, dropped and subjected
 *       to netsim. This <i>will</i> likely disconnect you; it exists for deep
 *       testing per the user's request.</li>
 * </ol>
 *
 * <p>Enforcement happens here, below the GUI, so hiding a control is never the
 * only line of defence. Every mutate/hold/delay/discard/netsim path must call
 * {@link #evaluate(PacketMeta, OperationType)} and honour a blocked decision.</p>
 */
public final class ProtectionPolicy {

    private final EssentialPacketMatcher matcher;
    private final AtomicBoolean advancedMode = new AtomicBoolean(false);
    private final AtomicBoolean unlockAllProtected = new AtomicBoolean(false);

    public ProtectionPolicy() {
        this(new EssentialPacketMatcher());
    }

    public ProtectionPolicy(EssentialPacketMatcher matcher) {
        this.matcher = matcher == null ? new EssentialPacketMatcher() : matcher;
    }

    public boolean isAdvancedMode() {
        return advancedMode.get();
    }

    public void setAdvancedMode(boolean on) {
        advancedMode.set(on);
    }

    public boolean isUnlockAllProtected() {
        return unlockAllProtected.get();
    }

    /**
     * Enable/disable the dangerous "unlock all protected packets" experiment
     * toggle. Enabling it does <b>not</b> enable Advanced Mode automatically.
     */
    public void setUnlockAllProtected(boolean on) {
        unlockAllProtected.set(on);
    }

    public EssentialPacketMatcher matcher() {
        return matcher;
    }

    public ProtectionTier classify(PacketMeta meta) {
        return matcher.classify(meta);
    }

    /**
     * Decide whether {@code op} may be performed against {@code meta}.
     */
    public ProtectionDecision evaluate(PacketMeta meta, OperationType op) {
        ProtectionTier tier = matcher.classify(meta);

        // Inspection is always allowed.
        if (op == null || !op.isMutatingOrControl()) {
            return ProtectionDecision.allow(tier);
        }

        boolean unlockAll = unlockAllProtected.get();

        if (tier == ProtectionTier.TIER1_ESSENTIAL) {
            if (unlockAll) {
                return ProtectionDecision.allow(tier,
                        "Protected packet overridden by 'Unlock ALL' \u2014 this may disconnect you.");
            }
            return ProtectionDecision.block(tier,
                    essentialBlockReason(meta, op));
        }

        // Tier-2 (standard) packet.
        if (advancedMode.get() || unlockAll) {
            return ProtectionDecision.allow(tier);
        }
        return ProtectionDecision.block(tier,
                "Enable Advanced Mode to " + verb(op) + " non-essential packets.");
    }

    /** Convenience: is this action currently allowed? */
    public boolean isAllowed(PacketMeta meta, OperationType op) {
        return evaluate(meta, op).allowed();
    }

    private String essentialBlockReason(PacketMeta meta, OperationType op) {
        return "\"" + meta.packetName() + "\" is a protected connection-integrity packet and "
                + "cannot be " + pastVerb(op) + " in this mode. "
                + "Enable 'Unlock ALL packets' to override (may break the connection).";
    }

    private static String verb(OperationType op) {
        switch (op) {
            case EDIT:          return "edit";
            case INTERCEPT:     return "hold";
            case DELAY:         return "delay";
            case DISCARD:       return "discard";
            case SIMULATE_LOSS: return "drop (simulated loss)";
            case THROTTLE:      return "throttle";
            default:            return "modify";
        }
    }

    private static String pastVerb(OperationType op) {
        switch (op) {
            case EDIT:          return "edited";
            case INTERCEPT:     return "held";
            case DELAY:         return "delayed";
            case DISCARD:       return "discarded";
            case SIMULATE_LOSS: return "dropped";
            case THROTTLE:      return "throttled";
            default:            return "modified";
        }
    }
}
