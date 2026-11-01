package com.packetauditor.protect;

/**
 * Classification of a packet for safety purposes.
 *
 * <ul>
 *   <li>{@link #TIER1_ESSENTIAL} — connection-integrity / handshake / liveness
 *       packets (KeepAlive, Ping/Pong, Login handshake, compression, config
 *       finish, disconnect). Protected in Normal and Advanced mode; only the
 *       explicit "Unlock ALL" experiment toggle can override them.</li>
 *   <li>{@link #TIER2_STANDARD} — everything else. Editable/holdable once
 *       Advanced Mode is enabled.</li>
 * </ul>
 */
public enum ProtectionTier {
    TIER1_ESSENTIAL,
    TIER2_STANDARD
}
