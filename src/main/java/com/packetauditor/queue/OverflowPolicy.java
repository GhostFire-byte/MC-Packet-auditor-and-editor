package com.packetauditor.queue;

/** Behaviour when the interception queue is full (spec §8). */
public enum OverflowPolicy {

    /** Reject the newest packet: forward it immediately instead of holding. */
    FORWARD_NEWEST,

    /** Evict and forward the oldest held packet to make room, then hold the new one. */
    RELEASE_OLDEST,

    /** Drop the newest packet entirely (only meaningful in Advanced/Unlock scenarios). */
    DROP_NEWEST
}
