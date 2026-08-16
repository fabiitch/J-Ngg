package com.nz.jnng.codec;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable registry of application message types. */
public final class MessageRegistry {

    private final Map<Integer, MessageType<?>> byId;
    private final Map<Class<?>, MessageType<?>> byJavaType;

    private MessageRegistry(
            Map<Integer, MessageType<?>> byId,
            Map<Class<?>, MessageType<?>> byJavaType
    ) {
        this.byId = Map.copyOf(byId);
        this.byJavaType = Map.copyOf(byJavaType);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<MessageType<?>> findById(int id) {
        return Optional.ofNullable(byId.get(id));
    }

    public MessageType<?> requireById(int id) {
        MessageType<?> type = byId.get(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown message type id: " + id);
        }
        return type;
    }

    public <T> Optional<MessageType<T>> findByJavaType(Class<T> javaType) {
        Objects.requireNonNull(javaType, "javaType");
        return Optional.ofNullable(cast(byJavaType.get(javaType)));
    }

    public <T> MessageType<T> requireByJavaType(Class<T> javaType) {
        return findByJavaType(javaType).orElseThrow(() ->
                new IllegalArgumentException("Unregistered Java message type: "
                        + javaType.getName()));
    }

    public MessageType<?> requireFor(Object message) {
        Objects.requireNonNull(message, "message");
        return requireByJavaType(message.getClass());
    }

    public EncodedPayload encode(Object message) {
        Objects.requireNonNull(message, "message");
        return encodeUnchecked(requireFor(message), message);
    }

    public Object decode(int messageTypeId, byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        return decodeUnchecked(requireById(messageTypeId), payload);
    }

    private static <T> EncodedPayload encodeUnchecked(MessageType<T> type, Object message) {
        T typedMessage = type.javaType().cast(message);
        return new EncodedPayload(type.id(), type.codec().encode(typedMessage));
    }

    private static <T> T decodeUnchecked(MessageType<T> type, byte[] payload) {
        return type.codec().decode(payload);
    }

    @SuppressWarnings("unchecked")
    private static <T> MessageType<T> cast(MessageType<?> type) {
        return (MessageType<T>) type;
    }

    public static final class Builder {
        private final Map<Integer, MessageType<?>> byId = new HashMap<>();
        private final Map<Class<?>, MessageType<?>> byJavaType = new HashMap<>();

        private Builder() {
        }

        public <T> Builder register(MessageType<T> type) {
            Objects.requireNonNull(type, "type");

            MessageType<?> idConflict = byId.putIfAbsent(type.id(), type);
            if (idConflict != null) {
                throw new IllegalArgumentException("Message type id " + type.id()
                        + " is already registered for " + idConflict.javaType().getName());
            }

            MessageType<?> classConflict = byJavaType.putIfAbsent(type.javaType(), type);
            if (classConflict != null) {
                byId.remove(type.id(), type);
                throw new IllegalArgumentException("Java message type "
                        + type.javaType().getName() + " is already registered with id "
                        + classConflict.id());
            }

            return this;
        }

        public MessageRegistry build() {
            return new MessageRegistry(byId, byJavaType);
        }
    }
}
