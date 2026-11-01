package com.packetauditor.model;

/**
 * Minecraft connection protocol phases (26.2). Packet identity is only unique
 * within a {@code (phase, direction)} pair, so the phase is tracked alongside
 * every captured packet.
 */
public enum ProtocolPhase {
    HANDSHAKE,
    STATUS,
    LOGIN,
    CONFIGURATION,
    PLAY,
    /** Used when the phase could not be determined. */
    UNKNOWN;

    public boolean isHandshakeOrLogin() {
        return this == HANDSHAKE || this == LOGIN;
    }
}
