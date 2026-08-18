package com.nz.jnng.socket;

/** Low-level snapshot of the active NNG pipe count for a socket. */
public record SocketConnectionEvent(int activeConnections) {
    public SocketConnectionEvent {
        if (activeConnections < 0) {
            throw new IllegalArgumentException("activeConnections must be >= 0");
        }
    }
}
