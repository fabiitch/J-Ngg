package com.nz.jnng.channel;

import com.nz.jnng.codec.NngMessageCodec;
import com.nz.jnng.message.NngMessage;
import com.nz.jnng.Subscription;
import com.nz.jnng.message.NngReceiveResult;
import com.nz.jnng.socket.INngSocket;
import com.nz.jnng.constants.NngErrorCode;
import com.nz.jnng.exception.NngException;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Bidirectional one-to-one communication channel using the NNG PAIR pattern.
 * <p>
 * A pair channel can both send and receive messages.
 * <p>
 * Messages can be consumed synchronously using {@link #receive()},
 * non-blockingly using {@link #tryReceive()}, or asynchronously using
 * {@link #onMessage(Consumer)}.
 * <p>
 * Only one receive mode should be used at a time for a given channel.
 */
public final class PairChannel implements AutoCloseable {

    private final INngSocket socket;
    private final NngMessageCodec codec;

    public PairChannel(INngSocket socket, NngMessageCodec codec) {
        this.socket = socket;
        this.codec = codec;
    }

    /**
     * Sends a message.
     * <p>
     * The call may block according to the underlying NNG socket configuration.
     *
     * @param message message to send
     */
    public void send(NngMessage message) {
        byte[] encoded = codec.encode(message);

        int rc = socket.send(encoded);

        if (rc != NngErrorCode.OK) {
            throw new NngException(rc);
        }
    }

    /**
     * Attempts to send a message without blocking.
     *
     * @param message message to send
     * @return {@code true} if the message was sent,
     * {@code false} if it could not be sent immediately
     */
    public boolean trySend(NngMessage message) {
        byte[] encoded = codec.encode(message);

        int rc = socket.trySend(encoded);

        if (rc == NngErrorCode.OK) {
            return true;
        }

        if (rc == NngErrorCode.EAGAIN) {
            return false;
        }
        throw new NngException(rc);
    }

    /**
     * Waits until a message is available and returns it.
     *
     * @return received message
     */
    public NngMessage receive() {
        NngReceiveResult result = socket.receive();

        if (result.code() != NngErrorCode.OK) {
            throw new NngException(result.code());
        }
        return codec.decode(result.data());
    }


    /**
     * Waits for a message up to the specified timeout.
     *
     * @param timeout maximum time to wait
     * @return received message, or empty if the timeout expires
     */
    public Optional<NngMessage> receive(Duration timeout) {
        NngReceiveResult result = socket.receive(timeout);

        if (result.code() == NngErrorCode.ETIMEDOUT) {
            return Optional.empty();
        }

        if (result.code() != NngErrorCode.OK) {
            throw new NngException(result.code());
        }

        return Optional.of(codec.decode(result.data()));
    }

    /**
     * Attempts to receive a message without blocking.
     *
     * @return the received message, or empty if no message is immediately available
     */
    public Optional<NngMessage> tryReceive() {
        NngReceiveResult result = socket.tryReceive();

        if (result.code() == NngErrorCode.EAGAIN) {
            return Optional.empty();
        }

        if (result.code() != NngErrorCode.OK) {
            throw new NngException(result.code());
        }

        return Optional.of(codec.decode(result.data()));
    }

    /**
     * Starts asynchronous message consumption.
     * <p>
     * The listener is invoked whenever a message is received.
     *
     * @param listener message listener
     * @return a subscription allowing asynchronous consumption to be stopped
     */
    public Subscription onMessage(Consumer<NngMessage> listener) {
        // TODO
        return null;
    }


    /**
     * Starts asynchronous message consumption using the specified executor
     * for listener execution.
     *
     * @param executor executor used to execute the listener
     * @param listener message listener
     * @return a subscription allowing asynchronous consumption to be stopped
     */
    public Subscription onMessage(
            Executor executor,
            Consumer<NngMessage> listener
    ) {
        // TODO
        return null;
    }

    /**
     * Closes the channel and releases its underlying NNG resources.
     */
    @Override
    public void close() {
        // TODO
    }
}