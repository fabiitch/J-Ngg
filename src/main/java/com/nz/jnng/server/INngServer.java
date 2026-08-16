package com.nz.jnng.server;

import com.nz.jnng.channel.*;

/**
 * Server-side NNG endpoint exposing named communication channels.
 * <p>
 * Each channel uses a specific NNG messaging pattern.
 * The server side is responsible for listening on the underlying endpoint.
 */
public interface INngServer extends AutoCloseable {

    /**
     * Creates a bidirectional one-to-one channel using the PAIR pattern.
     *
     * @param channel logical channel name
     * @return the pair channel
     */
    PairChannel pair(String channel);

    /**
     * Creates a publisher channel using the PUB pattern.
     * <p>
     * Messages sent on this channel are broadcast to all connected subscribers.
     *
     * @param channel logical channel name
     * @return the publisher channel
     */
    PublisherChannel pub(String channel);

    /**
     * Creates a subscriber channel using the SUB pattern.
     * <p>
     * Receives messages published by connected publishers.
     *
     * @param channel logical channel name
     * @return the subscriber channel
     */
    SubscriberChannel sub(String channel);

    /**
     * Creates a producer channel using the PUSH pattern.
     * <p>
     * Messages are distributed between connected PULL consumers.
     *
     * @param channel logical channel name
     * @return the push channel
     */
    PushChannel push(String channel);

    /**
     * Creates a consumer channel using the PULL pattern.
     * <p>
     * Receives messages distributed by connected PUSH producers.
     *
     * @param channel logical channel name
     * @return the pull channel
     */
    PullChannel pull(String channel);

    /**
     * Creates a request channel using the REQ pattern.
     * <p>
     * Sends a request and receives the corresponding reply.
     *
     * @param channel logical channel name
     * @return the request channel
     */
    RequestChannel req(String channel);

    /**
     * Creates a reply channel using the REP pattern.
     * <p>
     * Receives requests and sends corresponding replies.
     *
     * @param channel logical channel name
     * @return the reply channel
     */
    ReplyChannel rep(String channel);

    @Override
    void close();
}
