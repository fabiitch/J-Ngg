package com.nz.jnng.neww.communication;

import com.nz.jnng.Subscription;
import com.nz.jnng.neww.AbstractChannel;
import com.nz.jnng.neww.ChannelConfiguration;
import com.nz.jnng.neww.codec.ChannelMessageCodec;
import com.nz.jnng.neww.listener.ChannelRequestHandler;
import com.nz.jnng.socket.NativeMessage;
import com.nz.jnng.socket.impl.RepSocket;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** REP channel dispatching each request type to one response-producing handler. */
public final class RepChannel extends AbstractChannel {
    private final Map<Class<?>, HandlerRegistration<?>> handlers = new HashMap<>();
    private final AtomicReference<CompletableFuture<?>> pendingOperation = new AtomicReference<>();

    public RepChannel(ChannelConfiguration configuration, Executor executor) {
        super(configuration, executor, () -> new RepSocket(configuration.socketConfig()));
    }

    public <T> Subscription registerRequest(
            int messageTypeId,
            Class<T> messageType,
            ChannelMessageCodec<T> codec,
            ChannelRequestHandler<T> handler
    ) {
        Objects.requireNonNull(handler, "handler");
        registerMessage(messageTypeId, messageType, codec);
        HandlerRegistration<T> registration = new HandlerRegistration<>(messageType, handler);
        if (handlers.putIfAbsent(messageType, registration) != null) {
            throw new IllegalArgumentException("A request handler is already registered for "
                    + messageType.getName());
        }
        return registration;
    }

    @Override
    protected void onOpened() {
        armReceive();
    }

    @Override
    protected void onClosing() {
        CompletableFuture<?> operation = pendingOperation.getAndSet(null);
        if (operation != null) operation.cancel(true);
        handlers.values().forEach(HandlerRegistration::close);
        handlers.clear();
    }

    private void armReceive() {
        if (!isOpen()) return;
        CompletableFuture<NativeMessage> receive = socket().receiveNativeAsync();
        pendingOperation.set(receive);
        receive.whenComplete((message, error) -> {
            pendingOperation.compareAndSet(receive, null);
            if (!isOpen()) {
                if (message != null) message.close();
                return;
            }
            if (error != null) {
                if (!isClosingOrClosedError(error) && !isTimeoutError(error)) reportError(error);
                armReceive();
                return;
            }
            execute(() -> decodeHandleAndReply(message));
        });
    }

    private void decodeHandleAndReply(NativeMessage message) {
        ReceivedMessage request;
        try {
            request = decodeNativeMessage(message);
        } catch (Throwable decodeError) {
            failChannel(decodeError);
            return;
        }
        handleAndReply(request);
    }

    private void handleAndReply(ReceivedMessage request) {
        HandlerRegistration<?> registration = handlers.get(request.message().getClass());
        if (registration == null || !registration.isActive()) {
            failChannel(new IllegalStateException("No active request handler for "
                    + request.message().getClass().getName()));
            return;
        }

        Object response;
        try {
            response = Objects.requireNonNull(registration.invoke(request.message()),
                    "request handler response");
        } catch (Throwable handlerError) {
            failChannel(handlerError);
            return;
        }
        if (!isOpen()) return;

        CompletableFuture<Void> send;
        try {
            send = sendMessageAsync(response, request.messageId());
        } catch (Throwable encodeError) {
            failChannel(encodeError);
            return;
        }
        pendingOperation.set(send);
        send.whenComplete((ignored, error) -> {
            pendingOperation.compareAndSet(send, null);
            if (error != null && isOpen()) failChannel(error);
            else armReceive();
        });
    }

    private void failChannel(Throwable error) {
        reportError(error);
        execute(this::close);
    }

    private static final class HandlerRegistration<T> implements Subscription {
        private final Class<T> type;
        private final ChannelRequestHandler<T> handler;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private HandlerRegistration(Class<T> type, ChannelRequestHandler<T> handler) {
            this.type = type;
            this.handler = handler;
        }

        private Object invoke(Object message) throws Exception {
            return handler.onRequest(type.cast(message));
        }

        @Override
        public void close() { active.set(false); }

        @Override
        public boolean isActive() { return active.get(); }
    }
}
