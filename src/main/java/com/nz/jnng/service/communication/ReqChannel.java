package com.nz.jnng.service.communication;

import com.nz.jnng.exception.NggRequestTimeoutException;
import com.nz.jnng.exception.TooManyPendingRequestsException;
import com.nz.jnng.service.AbstractChannel;
import com.nz.jnng.service.ChannelConfiguration;
import com.nz.jnng.socket.NativeMessage;
import com.nz.jnng.socket.impl.ReqSocket;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Typed one-at-a-time REQ channel. */
public final class ReqChannel extends AbstractChannel {
    private final AtomicBoolean requestInFlight = new AtomicBoolean();

    public ReqChannel(ChannelConfiguration configuration, Executor executor) {
        super(configuration, executor, () -> new ReqSocket(configuration.socketConfig()));
    }

    public <R> R request(Object request, Class<R> responseType) {
        return join(requestAsync(request, responseType));
    }

    public <R> R request(Object request, Class<R> responseType, Duration timeout) {
        return join(requestAsync(request, responseType, timeout));
    }

    public <R> CompletableFuture<R> requestAsync(Object request, Class<R> responseType) {
        Duration timeout = configuration().socketConfig().requestTimeout().orElse(null);
        return requestAsyncInternal(request, responseType, timeout);
    }

    public <R> CompletableFuture<R> requestAsync(
            Object request,
            Class<R> responseType,
            Duration timeout
    ) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be > 0");
        }
        return requestAsyncInternal(request, responseType, timeout);
    }

    private <R> CompletableFuture<R> requestAsyncInternal(
            Object request,
            Class<R> responseType,
            Duration timeout
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(responseType, "responseType");
        ensureOpen();
        if (!requestInFlight.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new TooManyPendingRequestsException(1));
        }

        CompletableFuture<NativeMessage> receive;
        try {
            receive = sendMessageAsync(request)
                    .thenCompose(ignored -> timeout == null
                            ? socket().receiveNativeAsync()
                            : socket().receiveNativeAsync(timeout));
        } catch (Throwable error) {
            requestInFlight.set(false);
            return CompletableFuture.failedFuture(error);
        }

        CompletableFuture<R> result = dispatchCompletion(receive)
                .thenApply(message -> decodeNativeMessage(message, responseType))
                .handle((response, error) -> {
                    if (error == null) return response;
                    Throwable cause = unwrap(error);
                    if (isTimeoutError(cause)) {
                        throw new CompletionException(new NggRequestTimeoutException());
                    }
                    throw new CompletionException(cause);
                })
                .whenComplete((ignored, error) -> requestInFlight.set(false));
        return result;
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw error;
        }
    }
}
