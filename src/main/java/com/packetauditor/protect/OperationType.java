package com.packetauditor.protect;

/**
 * The kind of action being attempted against a packet. The protection layer
 * decides whether each action is permitted given the current mode and the
 * packet's {@link ProtectionTier}.
 */
public enum OperationType {

    /** Read-only: capture, decode, display. Always permitted. */
    INSPECT(false),

    /** Modify the packet (structured or raw edit). */
    EDIT(true),

    /** Hold the packet in the interception queue. */
    INTERCEPT(true),

    /** Delay the packet before forwarding. */
    DELAY(true),

    /** Drop the packet so it is never forwarded. */
    DISCARD(true),

    /** Simulated packet loss (netsim). */
    SIMULATE_LOSS(true),

    /** Bandwidth throttling (netsim). */
    THROTTLE(true);

    private final boolean mutatingOrControl;

    OperationType(boolean mutatingOrControl) {
        this.mutatingOrControl = mutatingOrControl;
    }

    /** True if this action changes or withholds the packet (i.e. must be gated). */
    public boolean isMutatingOrControl() {
        return mutatingOrControl;
    }
}
