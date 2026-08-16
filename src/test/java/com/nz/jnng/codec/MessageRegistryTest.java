package com.nz.jnng.codec;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageRegistryTest {

    private static final PayloadCodec<TestMessage> TEST_CODEC = new PayloadCodec<>() {
        @Override
        public byte[] encode(TestMessage value) {
            return value.value().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public TestMessage decode(byte[] payload) {
            return new TestMessage(new String(payload, StandardCharsets.UTF_8));
        }
    };

    @Test
    void resolvesARegisteredTypeByWireIdAndJavaClass() {
        MessageType<TestMessage> messageType =
                new MessageType<>(100, TestMessage.class, TEST_CODEC);
        MessageRegistry registry = MessageRegistry.builder()
                .register(messageType)
                .build();

        assertSame(messageType, registry.requireById(100));
        assertSame(messageType, registry.requireByJavaType(TestMessage.class));
        assertSame(messageType, registry.requireFor(new TestMessage("hello")));
        assertEquals(new TestMessage("hello"),
                messageType.codec().decode(messageType.codec().encode(new TestMessage("hello"))));
    }

    @Test
    void rejectsDuplicateWireIds() {
        MessageRegistry.Builder builder = MessageRegistry.builder()
                .register(new MessageType<>(100, TestMessage.class, TEST_CODEC));

        assertThrows(IllegalArgumentException.class, () -> builder.register(
                new MessageType<>(100, OtherMessage.class, passthroughOtherCodec())));
    }

    @Test
    void rejectsDuplicateJavaTypes() {
        MessageRegistry.Builder builder = MessageRegistry.builder()
                .register(new MessageType<>(100, TestMessage.class, TEST_CODEC));

        assertThrows(IllegalArgumentException.class, () -> builder.register(
                new MessageType<>(101, TestMessage.class, TEST_CODEC)));
    }

    @Test
    void reportsUnknownTypes() {
        MessageRegistry registry = MessageRegistry.builder().build();

        assertThrows(IllegalArgumentException.class, () -> registry.requireById(404));
        assertThrows(IllegalArgumentException.class,
                () -> registry.requireByJavaType(TestMessage.class));
    }

    private static PayloadCodec<OtherMessage> passthroughOtherCodec() {
        return new PayloadCodec<>() {
            @Override
            public byte[] encode(OtherMessage value) {
                return new byte[0];
            }

            @Override
            public OtherMessage decode(byte[] payload) {
                return new OtherMessage();
            }
        };
    }

    private record TestMessage(String value) {
    }

    private record OtherMessage() {
    }
}
