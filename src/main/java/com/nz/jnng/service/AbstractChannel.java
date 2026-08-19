package com.nz.jnng.service;

import com.nz.jnng.ConnectionMode;
import com.nz.jnng.Subscription;
import com.nz.jnng.constants.NngErrorCode;
import com.nz.jnng.exception.NngException;
import com.nz.jnng.service.codec.ChannelMessageCodec;
import com.nz.jnng.service.listener.ChannelConnectionEvent;
import com.nz.jnng.service.listener.ChannelConnectionListener;
import com.nz.jnng.service.listener.ChannelConnectionState;
import com.nz.jnng.service.listener.ChannelMessageListener;
import com.nz.jnng.socket.INngSocket;
import com.nz.jnng.socket.NativeMessage;
import com.nz.jnng.socket.NngCallbackBridge;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared lifecycle, message registry, transport and dispatch implementation. */
public abstract class AbstractChannel implements AutoCloseable {
    private enum Lifecycle { NEW, OPENING, OPEN, CLOSED }

    private final ChannelConfiguration configuration;
    private final Executor dispatcherExecutor;
    private final Supplier<? extends INngSocket> socketFactory;
    private final MessageDispatcher messages = new MessageDispatcher();
    private final CopyOnWriteArrayList<ChannelConnectionListener> connectionListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Throwable>> errorListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Subscription> messageSubscriptions =
            new CopyOnWriteArrayList<>();
    private final AtomicReference<Lifecycle> lifecycle = new AtomicReference<>(Lifecycle.NEW);
    private final AtomicLong messageIds = new AtomicLong();

    private volatile INngSocket socket;
    private volatile Subscription socketConnectionSubscription;
    private volatile ChannelConnectionEvent lastConnectionEvent;

    protected AbstractChannel(ChannelConfiguration configuration, Executor dispatcherExecutor,
                              Supplier<? extends INngSocket> socketFactory) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.dispatcherExecutor = Objects.requireNonNull(dispatcherExecutor, "dispatcherExecutor");
        this.socketFactory = Objects.requireNonNull(socketFactory, "socketFactory");
    }

    public final ChannelConfiguration configuration() {
        return configuration;
    }

    /** Registers the encoder/decoder for one message class. */
    public final <T> void registerMessage(int messageTypeId, Class<T> messageType,
                                          ChannelMessageCodec<T> codec) {
        ensureConfigurable();
        messages.register(messageTypeId, messageType, codec);
    }

    public final Subscription onConnectionChanged(ChannelConnectionListener listener) {
        Objects.requireNonNull(listener, "listener");
        ensureNotClosed();
        connectionListeners.add(listener);
        ListenerSubscription subscription = new ListenerSubscription(
                () -> connectionListeners.remove(listener));
        ChannelConnectionEvent current = lastConnectionEvent;
        if (current != null) execute(() -> listener.onConnectionChanged(current));
        return subscription;
    }

    public final Subscription onError(Consumer<Throwable> listener) {
        Objects.requireNonNull(listener, "listener");
        ensureNotClosed();
        errorListeners.add(listener);
        return new ListenerSubscription(() -> errorListeners.remove(listener));
    }

    /** Opens the native socket once, after all message registrations are installed. */
    public final void open() {
        if (!lifecycle.compareAndSet(Lifecycle.NEW, Lifecycle.OPENING)) {
            throw new IllegalStateException("Channel can only be opened once");
        }
        try {
            INngSocket openedSocket = socketFactory.get();
            socket = openedSocket;
            configureBeforeConnect(openedSocket);
            socketConnectionSubscription = openedSocket.onConnectionChanged(event ->
                    emitConnection(event.activeConnections() > 0
                                    ? ChannelConnectionState.CONNECTED
                                    : ChannelConnectionState.DISCONNECTED,
                            event.activeConnections()));
            emitConnection(ChannelConnectionState.CONNECTING, 0);
            int result = configuration.connectionMode() == ConnectionMode.LISTEN
                    ? openedSocket.listen(configuration.address())
                    : openedSocket.dial(configuration.address());
            check(result);
            lifecycle.set(Lifecycle.OPEN);
            onOpened();
        } catch (Throwable error) {
            lifecycle.set(Lifecycle.CLOSED);
            try {
                closeResources();
            } catch (Throwable closeError) {
                error.addSuppressed(closeError);
            } finally {
                emitConnection(ChannelConnectionState.CLOSED, 0);
            }
            throw error;
        }
    }

    public final boolean isOpen() {
        return lifecycle.get() == Lifecycle.OPEN;
    }

    protected void configureBeforeConnect(INngSocket socket) {
    }

    protected void onOpened() {
    }

    protected void onClosing() {
    }

    protected final <T> Subscription registerIncomingMessage(
            int messageTypeId, Class<T> messageType, ChannelMessageCodec<T> codec,
            ChannelMessageListener<T> listener) {
        ensureConfigurable();
        Subscription subscription = messages.register(
                messageTypeId, messageType, codec, listener);
        messageSubscriptions.add(subscription);
        return subscription;
    }

    protected final WireEnvelope encodeMessage(Object message) {
        return encodeMessage(message, 0);
    }

    protected final WireEnvelope encodeMessage(Object message, long correlationId) {
        return messages.encode(message, nextMessageId(), correlationId);
    }

    protected final Object decodeMessage(WireEnvelope envelope) {
        return messages.decode(envelope);
    }

    protected final <T> T decodeMessage(WireEnvelope envelope, Class<T> expectedType) {
        return messages.decode(envelope, expectedType);
    }

    protected final void dispatchMessage(WireEnvelope envelope) {
        messages.dispatch(envelope);
    }

    protected final void sendMessage(Object message) {
        sendEnvelope(encodeMessage(message));
    }

    protected final boolean trySendMessage(Object message) {
        ensureOpen();
        int result = socket.trySend(WireProtocol.encode(encodeMessage(message)));
        if (result == NngErrorCode.OK) return true;
        if (result == NngErrorCode.EAGAIN) return false;
        throw new NngException(result);
    }

    protected final CompletableFuture<Void> sendMessageAsync(Object message) {
        return sendEnvelopeAsync(encodeMessage(message));
    }

    protected final CompletableFuture<Void> sendMessageAsync(
            Object message,
            long correlationId
    ) {
        return sendEnvelopeAsync(encodeMessage(message, correlationId));
    }

    protected final void sendEnvelope(WireEnvelope envelope) {
        ensureOpen();
        check(socket.send(WireProtocol.encode(envelope)));
    }

    protected final CompletableFuture<Void> sendEnvelopeAsync(WireEnvelope envelope) {
        ensureOpen();
        return dispatchCompletion(socket.sendAsync(WireProtocol.encode(envelope)));
    }

    protected final WireEnvelope decodeEnvelope(byte[] bytes) {
        return WireProtocol.decode(bytes);
    }

    protected final WireEnvelope decodeEnvelope(NativeMessage message) {
        try (message) {
            return WireProtocol.decode(message.toByteArray());
        }
    }

    protected final void dispatchNativeMessage(NativeMessage message) {
        dispatchMessage(decodeEnvelope(message));
    }

    protected final ReceivedMessage decodeNativeMessage(NativeMessage message) {
        WireEnvelope envelope = decodeEnvelope(message);
        return new ReceivedMessage(decodeMessage(envelope), envelope.messageId());
    }

    protected final <T> T decodeNativeMessage(NativeMessage message, Class<T> expectedType) {
        return decodeMessage(decodeEnvelope(message), expectedType);
    }

    protected final INngSocket socket() {
        ensureOpen();
        return socket;
    }

    protected final void execute(Runnable task) {
        NngCallbackBridge.execute(() -> {
            try {
                dispatcherExecutor.execute(task);
            } catch (RejectedExecutionException error) {
                if (lifecycle.get() != Lifecycle.CLOSED) reportErrorDirect(error);
            }
        });
    }

    protected final <T> CompletableFuture<T> dispatchCompletion(CompletableFuture<T> source) {
        CompletableFuture<T> dispatched = new CompletableFuture<>();
        source.whenComplete((value, error) -> execute(() -> {
            if (error == null) dispatched.complete(value);
            else dispatched.completeExceptionally(unwrap(error));
        }));
        return dispatched;
    }

    protected final void reportError(Throwable error) {
        Throwable cause = unwrap(error);
        execute(() -> reportErrorDirect(cause));
    }

    protected final boolean isClosingOrClosedError(Throwable error) {
        Throwable cause = unwrap(error);
        return cause instanceof NngException nngError
                && (nngError.getCode() == NngErrorCode.ECLOSED
                || nngError.getCode() == NngErrorCode.ECANCELED);
    }

    protected final boolean isTimeoutError(Throwable error) {
        Throwable cause = unwrap(error);
        return cause instanceof NngException nngError
                && nngError.getCode() == NngErrorCode.ETIMEDOUT;
    }

    protected final Throwable unwrap(Throwable error) {
        return error.getCause() == null ? error : error.getCause();
    }

    protected final void ensureOpen() {
        if (lifecycle.get() != Lifecycle.OPEN) {
            throw new IllegalStateException("Channel is not open");
        }
    }

    @Override
    public final void close() {
        Lifecycle previous = lifecycle.getAndSet(Lifecycle.CLOSED);
        if (previous == Lifecycle.CLOSED) return;
        try {
            onClosing();
        } finally {
            messageSubscriptions.forEach(Subscription::close);
            messageSubscriptions.clear();
            try {
                closeResources();
            } finally {
                emitConnection(ChannelConnectionState.CLOSED, 0);
            }
        }
    }

    private void closeResources() {
        Subscription connectionSubscription = socketConnectionSubscription;
        socketConnectionSubscription = null;
        if (connectionSubscription != null) connectionSubscription.close();
        INngSocket currentSocket = socket;
        socket = null;
        if (currentSocket != null) currentSocket.close();
    }

    private void ensureConfigurable() {
        if (lifecycle.get() != Lifecycle.NEW) {
            throw new IllegalStateException("Messages must be registered before open()");
        }
    }

    private void ensureNotClosed() {
        if (lifecycle.get() == Lifecycle.CLOSED) {
            throw new IllegalStateException("Channel is closed");
        }
    }

    private long nextMessageId() {
        return messageIds.updateAndGet(current -> current == Long.MAX_VALUE ? 1 : current + 1);
    }

    private void emitConnection(ChannelConnectionState state, int activeConnections) {
        ChannelConnectionEvent event = new ChannelConnectionEvent(state, activeConnections);
        lastConnectionEvent = event;
        execute(() -> {
            for (ChannelConnectionListener listener : connectionListeners) {
                try {
                    listener.onConnectionChanged(event);
                } catch (Throwable error) {
                    reportErrorDirect(error);
                }
            }
        });
    }

    private void reportErrorDirect(Throwable error) {
        if (errorListeners.isEmpty()) {
            Thread current = Thread.currentThread();
            current.getUncaughtExceptionHandler().uncaughtException(current, error);
            return;
        }
        for (Consumer<Throwable> listener : errorListeners) {
            try {
                listener.accept(error);
            } catch (Throwable listenerError) {
                Thread current = Thread.currentThread();
                current.getUncaughtExceptionHandler().uncaughtException(current, listenerError);
            }
        }
    }

    private static void check(int result) {
        if (result != NngErrorCode.OK) throw new NngException(result);
    }

    protected record ReceivedMessage(Object message, long messageId) {
    }

    private final class ListenerSubscription implements Subscription {
        private final Runnable removal;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private ListenerSubscription(Runnable removal) {
            this.removal = removal;
        }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) removal.run();
        }

        @Override
        public boolean isActive() {
            return active.get() && lifecycle.get() != Lifecycle.CLOSED;
        }
    }
}
