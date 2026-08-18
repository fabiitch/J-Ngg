package com.nz.jnng.neww;

import com.nz.jnng.Subscription;
import com.nz.jnng.neww.codec.ChannelMessageCodec;
import com.nz.jnng.neww.listener.ChannelMessageListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class MessageDispatcher {
    private final Map<Integer, Registration<?>> byId = new HashMap<>();
    private final Map<Class<?>, Registration<?>> byClass = new HashMap<>();

    <T> void register(int id, Class<T> type, ChannelMessageCodec<T> codec) {
        registerInternal(id, type, codec, null);
    }

    <T> Subscription register(int id, Class<T> type, ChannelMessageCodec<T> codec,
                              ChannelMessageListener<T> listener) {
        Registration<T> registration = registerInternal(id, type, codec,
                Objects.requireNonNull(listener, "listener"));
        return new ListenerSubscription(registration, listener);
    }

    WireEnvelope encode(Object message, long messageId, long correlationId) {
        Objects.requireNonNull(message, "message");
        Registration<Object> registration = registrationFor(message.getClass());
        byte[] payload = registration.codec.encode(registration.type.cast(message));
        return new WireEnvelope(WireProtocol.VERSION, registration.id, messageId,
                correlationId, Objects.requireNonNull(payload, "codec payload"));
    }

    Object decode(WireEnvelope envelope) {
        Registration<?> registration = byId.get(envelope.messageTypeId());
        if (registration == null) {
            throw new IllegalArgumentException("Unknown message type id: "
                    + envelope.messageTypeId());
        }
        return registration.decode(envelope.payload());
    }

    <T> T decode(WireEnvelope envelope, Class<T> expectedType) {
        Object decoded = decode(envelope);
        if (!expectedType.isInstance(decoded)) {
            throw new IllegalArgumentException("Expected response " + expectedType.getName()
                    + " but received " + decoded.getClass().getName());
        }
        return expectedType.cast(decoded);
    }

    void dispatch(WireEnvelope envelope) {
        Registration<?> registration = byId.get(envelope.messageTypeId());
        if (registration == null) {
            throw new IllegalArgumentException("Unknown message type id: "
                    + envelope.messageTypeId());
        }
        registration.dispatch(envelope.payload());
    }

    private <T> Registration<T> registerInternal(int id, Class<T> type,
                                                  ChannelMessageCodec<T> codec,
                                                  ChannelMessageListener<T> listener) {
        if (id <= 0) throw new IllegalArgumentException("message type id must be > 0");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(codec, "codec");
        if (byId.containsKey(id)) {
            throw new IllegalArgumentException("Message type id " + id + " is already registered");
        }
        if (byClass.containsKey(type)) {
            throw new IllegalArgumentException("Message class " + type.getName()
                    + " is already registered");
        }
        Registration<T> registration = new Registration<>(id, type, codec, listener);
        byId.put(id, registration);
        byClass.put(type, registration);
        return registration;
    }

    @SuppressWarnings("unchecked")
    private Registration<Object> registrationFor(Class<?> type) {
        Registration<?> registration = byClass.get(type);
        if (registration == null) {
            throw new IllegalArgumentException("Unregistered message class: " + type.getName());
        }
        return (Registration<Object>) registration;
    }

    private static final class Registration<T> {
        private final int id;
        private final Class<T> type;
        private final ChannelMessageCodec<T> codec;
        private final AtomicReference<ChannelMessageListener<T>> listener;

        private Registration(int id, Class<T> type, ChannelMessageCodec<T> codec,
                             ChannelMessageListener<T> listener) {
            this.id = id;
            this.type = type;
            this.codec = codec;
            this.listener = new AtomicReference<>(listener);
        }

        private T decode(byte[] payload) {
            return Objects.requireNonNull(codec.decode(payload), "decoded message");
        }

        private void dispatch(byte[] payload) {
            ChannelMessageListener<T> target = listener.get();
            if (target == null) return;
            target.onMessage(decode(payload));
        }
    }

    private static final class ListenerSubscription implements Subscription {
        private final Registration<?> registration;
        private final Object listener;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private ListenerSubscription(Registration<?> registration, Object listener) {
            this.registration = registration;
            this.listener = listener;
        }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) clearListener(registration, listener);
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static void clearListener(Registration registration, Object listener) {
            registration.listener.compareAndSet(listener, null);
        }
    }
}
