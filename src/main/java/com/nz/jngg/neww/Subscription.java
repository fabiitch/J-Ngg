package com.nz.jngg.neww;

/**
 * Represents an active asynchronous channel subscription.
 */
public interface Subscription extends AutoCloseable {

    /**
     * Stops the subscription.
     */
    @Override
    void close();

    /**
     * Indicates whether the subscription is currently active.
     *
     * @return {@code true} if active
     */
    boolean isActive();
}