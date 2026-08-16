package com.nz.jnng.service;

import com.nz.jnng.Subscription;
import com.nz.jnng.channel.PairChannel;
import com.nz.jnng.codec.BinaryWireCodec;
import com.nz.jnng.codec.EncodedPayload;
import com.nz.jnng.codec.MessageRegistry;
import com.nz.jnng.codec.MessageType;
import com.nz.jnng.codec.WireCodec;
import com.nz.jnng.message.NativeWireMessage;
import com.nz.jnng.message.WireMessage;
import com.nz.jnng.server.NngChannelFactory;
import com.nz.jnng.socket.NngSocketConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** High-level typed IPC facade used by an application executable. */
public final class NngService<E> implements AutoCloseable {
    public static final int WIRE_VERSION = 1;

    private final E self;
    private final MessageRegistry registry;
    private final Map<E, PairChannel> channels = new ConcurrentHashMap<>();
    private final List<Subscription> channelSubscriptions = new CopyOnWriteArrayList<>();
    private final Map<HandlerKey<E>, CopyOnWriteArrayList<TypedHandler<?>>> typedHandlers =
            new ConcurrentHashMap<>();
    private final Map<HandlerKey<E>, CopyOnWriteArrayList<Consumer<NativeWireMessage>>> rawHandlers =
            new ConcurrentHashMap<>();
    private final AtomicLong messageIds = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private final Consumer<Throwable> errorHandler;

    private NngService(Builder<E> builder) {
        self = builder.self;
        registry = builder.registry;
        errorHandler = builder.errorHandler;
        if (builder.executor == null) {
            ownedExecutor = Executors.newSingleThreadExecutor(runnable ->
                    Thread.ofPlatform().daemon(true)
                            .name("jnng-service-" + self).unstarted(runnable));
            executor = ownedExecutor;
        } else {
            ownedExecutor = null;
            executor = builder.executor;
        }

        NngChannelFactory channelFactory = new NngChannelFactory(
                builder.wireCodec,
                builder.socketConfig
        );
        try {
            for (NngTopology.Link<E> link : builder.topology.linksFor(self)) {
                E peer = link.peerOf(self);
                PairChannel channel = channelFactory.createPair(link.address(), link.modeOf(self));
                if (channels.putIfAbsent(peer, channel) != null) {
                    channel.close();
                    throw new IllegalArgumentException("Duplicate channel to " + peer);
                }
                channelSubscriptions.add(channel.onNativeMessage(
                        executor,
                        message -> dispatch(peer, message)
                ));
            }
        } catch (Throwable error) {
            close();
            throw error;
        }
    }

    public static <E> Builder<E> builder(
            E self,
            NngTopology<E> topology,
            MessageRegistry registry
    ) {
        return new Builder<>(self, topology, registry);
    }

    public static <E> NngService<E> open(
            E self,
            NngTopology<E> topology,
            MessageRegistry registry
    ) {
        return builder(self, topology, registry).build();
    }

    public E self() {
        return self;
    }

    public void sendTo(E target, Object message) {
        EncodedPayload payload = registry.encode(message);
        channelTo(target).send(envelope(payload));
    }

    public CompletableFuture<Void> sendToAsync(E target, Object message) {
        EncodedPayload payload = registry.encode(message);
        return channelTo(target).sendAsync(envelope(payload));
    }

    public <T> Subscription on(Class<T> messageClass, Consumer<T> handler) {
        return onInternal(null, messageClass, handler);
    }

    public <T> Subscription on(E source, Class<T> messageClass, Consumer<T> handler) {
        Objects.requireNonNull(source, "source");
        return onInternal(source, messageClass, handler);
    }

    /**
     * Registers a zero-copy handler. The payload segment is valid only for the
     * duration of the callback and the callback must not close the message.
     */
    public Subscription onNativeMessage(
            E source,
            int messageTypeId,
            Consumer<NativeWireMessage> handler
    ) {
        ensureOpen();
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(handler, "handler");
        HandlerKey<E> key = new HandlerKey<>(source, messageTypeId);
        CopyOnWriteArrayList<Consumer<NativeWireMessage>> handlers =
                rawHandlers.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
        handlers.add(handler);
        return removal(() -> {
            handlers.remove(handler);
            if (handlers.isEmpty()) rawHandlers.remove(key, handlers);
        });
    }

    private <T> Subscription onInternal(
            E source,
            Class<T> messageClass,
            Consumer<T> handler
    ) {
        ensureOpen();
        Objects.requireNonNull(messageClass, "messageClass");
        Objects.requireNonNull(handler, "handler");
        MessageType<T> messageType = registry.requireByJavaType(messageClass);
        HandlerKey<E> key = new HandlerKey<>(source, messageType.id());
        TypedHandler<T> typedHandler = new TypedHandler<>(messageClass, handler);
        CopyOnWriteArrayList<TypedHandler<?>> handlers =
                typedHandlers.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
        handlers.add(typedHandler);
        return removal(() -> {
            handlers.remove(typedHandler);
            if (handlers.isEmpty()) typedHandlers.remove(key, handlers);
        });
    }

    private WireMessage envelope(EncodedPayload payload) {
        long messageId = messageIds.updateAndGet(current ->
                current == Long.MAX_VALUE ? 1 : current + 1);
        return new WireMessage(
                WIRE_VERSION,
                payload.messageTypeId(),
                messageId,
                0,
                payload.bytes()
        );
    }

    private PairChannel channelTo(E target) {
        ensureOpen();
        Objects.requireNonNull(target, "target");
        PairChannel channel = channels.get(target);
        if (channel == null) {
            throw new IllegalArgumentException("No topology link from " + self + " to " + target);
        }
        return channel;
    }

    private void dispatch(E source, NativeWireMessage message) {
        HandlerKey<E> exact = new HandlerKey<>(source, message.messageTypeId());
        HandlerKey<E> anySource = new HandlerKey<>(null, message.messageTypeId());

        invokeRaw(rawHandlers.get(exact), message);
        invokeRaw(rawHandlers.get(anySource), message);

        List<TypedHandler<?>> handlers = new ArrayList<>();
        addAll(handlers, typedHandlers.get(exact));
        addAll(handlers, typedHandlers.get(anySource));
        if (handlers.isEmpty()) return;

        Object decoded;
        try {
            decoded = registry.decode(message.messageTypeId(), message.copyPayload());
        } catch (Throwable error) {
            errorHandler.accept(error);
            return;
        }
        for (TypedHandler<?> handler : handlers) handler.invoke(decoded, errorHandler);
    }

    private void invokeRaw(
            List<Consumer<NativeWireMessage>> handlers,
            NativeWireMessage message
    ) {
        if (handlers == null) return;
        for (Consumer<NativeWireMessage> handler : handlers) {
            try {
                handler.accept(message);
            } catch (Throwable error) {
                errorHandler.accept(error);
            }
        }
    }

    private static <T> void addAll(List<T> target, List<T> values) {
        if (values != null) target.addAll(values);
    }

    private static Subscription removal(Runnable removal) {
        return new Subscription() {
            private final AtomicBoolean active = new AtomicBoolean(true);
            @Override public void close() {
                if (active.compareAndSet(true, false)) removal.run();
            }
            @Override public boolean isActive() { return active.get(); }
        };
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("NngService is closed");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        channelSubscriptions.forEach(Subscription::close);
        channelSubscriptions.clear();
        channels.values().forEach(PairChannel::close);
        channels.clear();
        if (ownedExecutor != null) ownedExecutor.shutdownNow();
    }

    private record HandlerKey<E>(E source, int messageTypeId) {
    }

    private record TypedHandler<T>(Class<T> type, Consumer<T> consumer) {
        void invoke(Object message, Consumer<Throwable> errorHandler) {
            try {
                consumer.accept(type.cast(message));
            } catch (Throwable error) {
                errorHandler.accept(error);
            }
        }
    }

    public static final class Builder<E> {
        private final E self;
        private final NngTopology<E> topology;
        private final MessageRegistry registry;
        private WireCodec wireCodec = new BinaryWireCodec();
        private NngSocketConfig socketConfig = NngSocketConfig.defaults();
        private Executor executor;
        private Consumer<Throwable> errorHandler = error ->
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), error);

        private Builder(E self, NngTopology<E> topology, MessageRegistry registry) {
            this.self = Objects.requireNonNull(self, "self");
            this.topology = Objects.requireNonNull(topology, "topology");
            this.registry = Objects.requireNonNull(registry, "registry");
        }

        public Builder<E> wireCodec(WireCodec wireCodec) {
            this.wireCodec = Objects.requireNonNull(wireCodec, "wireCodec");
            return this;
        }

        public Builder<E> executor(Executor executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        public Builder<E> socketConfig(NngSocketConfig socketConfig) {
            this.socketConfig = Objects.requireNonNull(socketConfig, "socketConfig");
            return this;
        }

        public Builder<E> onError(Consumer<Throwable> errorHandler) {
            this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
            return this;
        }

        public NngService<E> build() {
            return new NngService<>(this);
        }
    }
}
