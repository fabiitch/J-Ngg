package com.nz.jnng.channel;

import com.nz.jnng.Subscription;
import com.nz.jnng.codec.WireCodec;
import com.nz.jnng.constants.NngErrorCode;
import com.nz.jnng.exception.NngException;
import com.nz.jnng.message.NativeWireMessage;
import com.nz.jnng.message.NngReceiveResult;
import com.nz.jnng.message.WireMessage;
import com.nz.jnng.socket.INngSocket;
import com.nz.jnng.socket.NativeMessage;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

abstract class AbstractChannel implements AutoCloseable {
    protected final INngSocket socket;
    protected final WireCodec codec;

    AbstractChannel(INngSocket socket, WireCodec codec) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    protected final void sendMessage(WireMessage message) {
        try (NativeMessage nativeMessage = codec.encodeNative(message)) {
            check(socket.send(nativeMessage));
        }
    }

    protected final boolean trySendMessage(WireMessage message) {
        int rc = socket.trySend(codec.encode(message));
        if (rc == NngErrorCode.OK) return true;
        if (rc == NngErrorCode.EAGAIN) return false;
        throw new NngException(rc);
    }

    protected final CompletableFuture<Void> sendMessageAsync(WireMessage message) {
        return socket.sendAsync(codec.encode(message));
    }

    protected final WireMessage receiveMessage() {
        return decode(socket.receive());
    }

    protected final Optional<WireMessage> receiveMessage(Duration timeout) {
        NngReceiveResult result = socket.receive(timeout);
        if (result.code() == NngErrorCode.ETIMEDOUT) return Optional.empty();
        return Optional.of(decode(result));
    }

    protected final Optional<WireMessage> tryReceiveMessage() {
        NngReceiveResult result = socket.tryReceive();
        if (result.code() == NngErrorCode.EAGAIN) return Optional.empty();
        return Optional.of(decode(result));
    }

    protected final Subscription subscribe(Consumer<WireMessage> listener) {
        return subscribe(NngChannelExecutors.shared(), listener);
    }

    protected final Subscription subscribe(Executor executor, Consumer<WireMessage> listener) {
        Objects.requireNonNull(listener, "listener");
        return subscribeNative(executor, nativeMessage -> listener.accept(new WireMessage(
                nativeMessage.wireVersion(), nativeMessage.messageTypeId(),
                nativeMessage.messageId(), nativeMessage.correlationId(),
                nativeMessage.copyPayload())));
    }

    protected final Subscription subscribeNative(Consumer<NativeWireMessage> listener) {
        return subscribeNative(NngChannelExecutors.shared(), listener);
    }

    protected final Subscription subscribeNative(
            Executor executor,
            Consumer<NativeWireMessage> listener
    ) {
        return new ChannelSubscription(socket, codec, executor, listener,
                error -> Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), error));
    }

    private WireMessage decode(NngReceiveResult result) {
        check(result.code());
        return codec.decode(result.data());
    }

    private static void check(int rc) {
        if (rc != NngErrorCode.OK) throw new NngException(rc);
    }

    @Override
    public void close() {
        socket.close();
    }
}
