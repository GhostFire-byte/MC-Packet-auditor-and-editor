package com.packetauditor.api.event;

/** Types of connection events exposed to the host (spec §4, D12). */
public enum ConnectionEventType {
    CONNECT,
    DISCONNECT,
    PROTOCOL_STATE_CHANGE,
    LOGIN_SUCCESS,
    SERVER_ADDRESS,
    LATENCY,
    COMPRESSION_ENABLED,
    KEEPALIVE_TIMING
}
