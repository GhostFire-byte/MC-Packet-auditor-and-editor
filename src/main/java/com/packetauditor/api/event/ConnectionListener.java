package com.packetauditor.api.event;

/** Listener for connection-monitoring events. */
@FunctionalInterface
public interface ConnectionListener {
    void onConnectionEvent(ConnectionEvent event);
}
