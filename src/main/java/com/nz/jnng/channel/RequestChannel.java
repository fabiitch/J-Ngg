package com.nz.jnng.channel;

import com.nz.jnng.codec.WireCodec;
import com.nz.jnng.exception.TooManyPendingRequestsException;
import com.nz.jnng.exception.NggRequestTimeoutException;
import com.nz.jnng.message.NativeWireMessage;
import com.nz.jnng.message.WireMessage;
import com.nz.jnng.socket.INngSocket;
import com.nz.jnng.socket.NativeMessage;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import com.nz.jnng.exception.NngException;
import com.nz.jnng.constants.NngErrorCode;

/** One-at-a-time REQ transaction channel. */
public final class RequestChannel extends AbstractChannel {
    private final AtomicBoolean transactionInFlight = new AtomicBoolean();

    public RequestChannel(INngSocket socket, WireCodec codec) { super(socket, codec); }

    public WireMessage request(WireMessage request) {
        beginTransaction();
        try {
            sendMessage(request);
            return socket.config().requestTimeout()
                    .map(timeout -> receiveMessage(timeout)
                            .orElseThrow(NggRequestTimeoutException::new))
                    .orElseGet(this::receiveMessage);
        } finally {
            transactionInFlight.set(false);
        }
    }

    public Optional<WireMessage> request(WireMessage request, Duration timeout) {
        beginTransaction();
        try {
            sendMessage(request);
            return receiveMessage(timeout);
        } finally {
            transactionInFlight.set(false);
        }
    }

    public CompletableFuture<WireMessage> requestAsync(WireMessage request) {
        if (!transactionInFlight.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new TooManyPendingRequestsException(1));
        }

        CompletableFuture<WireMessage> result = sendMessageAsync(request)
                .thenCompose(ignored -> socket.config().requestTimeout()
                        .map(socket::receiveNativeAsync)
                        .orElseGet(socket::receiveNativeAsync))
                .thenApply(this::decodeAndClose);
        return result.handle((response, error) -> {
            if (error == null) return response;
            Throwable cause = error.getCause() == null ? error : error.getCause();
            if (cause instanceof NngException nngError
                    && nngError.getCode() == NngErrorCode.ETIMEDOUT) {
                throw new CompletionException(new NggRequestTimeoutException());
            }
            throw new CompletionException(cause);
        }).whenComplete((ignored, error) -> transactionInFlight.set(false));
    }

    private void beginTransaction() {
        if (!transactionInFlight.compareAndSet(false, true)) {
            throw new TooManyPendingRequestsException(1);
        }
    }

    private WireMessage decodeAndClose(NativeMessage message) {
        try (message) {
            try (NativeWireMessage nativeWire = codec.decodeNative(message)) {
                return new WireMessage(
                        nativeWire.wireVersion(),
                        nativeWire.messageTypeId(),
                        nativeWire.messageId(),
                        nativeWire.correlationId(),
                        nativeWire.copyPayload()
                );
            }
        }
    }
}
