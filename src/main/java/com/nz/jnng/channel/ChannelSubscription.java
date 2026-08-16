package com.nz.jnng.channel;

import com.nz.jnng.Subscription;
import com.nz.jnng.codec.WireCodec;
import com.nz.jnng.constants.NngErrorCode;
import com.nz.jnng.exception.NngException;
import com.nz.jnng.message.NativeWireMessage;
import com.nz.jnng.socket.INngSocket;
import com.nz.jnng.socket.NativeMessage;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class ChannelSubscription implements Subscription {
    private final INngSocket socket;
    private final WireCodec codec;
    private final Executor executor;
    private final Consumer<NativeWireMessage> listener;
    private final Consumer<Throwable> errorHandler;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicReference<CompletableFuture<NativeMessage>> pending =
            new AtomicReference<>();

    ChannelSubscription(
            INngSocket socket,
            WireCodec codec,
            Executor executor,
            Consumer<NativeWireMessage> listener,
            Consumer<Throwable> errorHandler
    ) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        arm();
    }

    private void arm() {
        if (!active.get()) {
            return;
        }
        CompletableFuture<NativeMessage> operation = socket.receiveNativeAsync();
        pending.set(operation);
        operation.whenComplete((message, error) -> {
            pending.compareAndSet(operation, null);
            if (!active.get()) {
                if (message != null) {
                    message.close();
                }
                return;
            }
            if (error != null) {
                Throwable cause = unwrap(error);
                if (cause instanceof NngException nngError
                        && (nngError.getCode() == NngErrorCode.ECLOSED
                        || nngError.getCode() == NngErrorCode.ECANCELED)) {
                    active.set(false);
                } else if (cause instanceof NngException nngError
                        && nngError.getCode() == NngErrorCode.ETIMEDOUT) {
                    arm();
                } else {
                    errorHandler.accept(cause);
                    arm();
                }
                return;
            }

            NativeWireMessage wireMessage;
            try {
                wireMessage = codec.decodeNative(message);
            } catch (Throwable decodeError) {
                message.close();
                errorHandler.accept(decodeError);
                arm();
                return;
            }

            arm();
            try {
                executor.execute(() -> {
                    try (wireMessage) {
                        listener.accept(wireMessage);
                    } catch (Throwable handlerError) {
                        errorHandler.accept(handlerError);
                    }
                });
            } catch (RejectedExecutionException rejected) {
                wireMessage.close();
                errorHandler.accept(rejected);
            }
        });
    }

    @Override
    public void close() {
        if (active.compareAndSet(true, false)) {
            CompletableFuture<NativeMessage> operation = pending.getAndSet(null);
            if (operation != null) {
                operation.cancel(true);
            }
        }
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    private static Throwable unwrap(Throwable error) {
        return error.getCause() == null ? error : error.getCause();
    }
}
