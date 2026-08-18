package com.nz.jnng.neww.listener;

/** Snapshot emitted when the number of active NNG pipes changes. */
public record ChannelConnectionEvent(
        ChannelConnectionState state,
        int activeConnections
) {
    public ChannelConnectionEvent {
        if (activeConnections < 0) {
            throw new IllegalArgumentException("activeConnections must be >= 0");
        }
    }
}
