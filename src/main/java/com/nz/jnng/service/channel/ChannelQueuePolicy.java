package com.nz.jnng.service.channel;

import java.util.Objects;

/** Bounds messages waiting for application dispatch on a receiving channel. */
public record ChannelQueuePolicy(int capacity, OverflowStrategy overflowStrategy) {
    public enum OverflowStrategy {
        /** Keep already accepted messages and discard the newly received message. */
        DROP_NEWEST
    }

    public ChannelQueuePolicy {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        Objects.requireNonNull(overflowStrategy, "overflowStrategy");
    }

    public static ChannelQueuePolicy dropNewest(int capacity) {
        return new ChannelQueuePolicy(capacity, OverflowStrategy.DROP_NEWEST);
    }

    public static ChannelQueuePolicy defaults() {
        return dropNewest(256);
    }
}
