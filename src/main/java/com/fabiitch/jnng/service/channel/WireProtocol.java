package com.fabiitch.jnng.service.channel;

import com.fabiitch.jnng.socket.NativeMessage;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

final class WireProtocol {
    static final int VERSION = 1;
    static final int HEADER_SIZE = Integer.BYTES * 3 + Long.BYTES * 2;
    private static final ValueLayout.OfInt NETWORK_INT =
            ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong NETWORK_LONG =
            ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    private WireProtocol() {
    }

    static byte[] encode(WireEnvelope message) {
        Objects.requireNonNull(message, "message");
        byte[] payload = message.payload();
        return ByteBuffer.allocate(Math.addExact(HEADER_SIZE, payload.length))
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(message.version()).putInt(message.messageTypeId())
                .putLong(message.messageId()).putLong(message.correlationId())
                .putInt(payload.length).put(payload).array();
    }

    static WireEnvelope decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Message is shorter than the wire header");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int version = buffer.getInt();
        validateVersion(version);
        int typeId = buffer.getInt();
        long messageId = buffer.getLong();
        long correlationId = buffer.getLong();
        int payloadLength = buffer.getInt();
        if (payloadLength < 0 || payloadLength != buffer.remaining()) {
            throw new IllegalArgumentException("Invalid payload length: header="
                    + payloadLength + ", actual=" + buffer.remaining());
        }
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        return new WireEnvelope(version, typeId, messageId, correlationId, payload);
    }

    /** Reads the fixed header in native memory and only copies the application payload. */
    static WireEnvelope decode(NativeMessage message) {
        Objects.requireNonNull(message, "message");
        MemorySegment body = message.body();
        long size = body.byteSize();
        if (size < HEADER_SIZE) {
            throw new IllegalArgumentException("Message is shorter than the wire header");
        }
        int version = body.get(NETWORK_INT, 0);
        validateVersion(version);
        int typeId = body.get(NETWORK_INT, Integer.BYTES);
        long messageId = body.get(NETWORK_LONG, Integer.BYTES * 2L);
        long correlationId = body.get(NETWORK_LONG, Integer.BYTES * 2L + Long.BYTES);
        int payloadLength = body.get(NETWORK_INT, HEADER_SIZE - Integer.BYTES);
        long actualLength = size - HEADER_SIZE;
        if (payloadLength < 0 || payloadLength != actualLength) {
            throw new IllegalArgumentException("Invalid payload length: header="
                    + payloadLength + ", actual=" + actualLength);
        }
        byte[] payload = body.asSlice(HEADER_SIZE, payloadLength)
                .toArray(ValueLayout.JAVA_BYTE);
        return new WireEnvelope(version, typeId, messageId, correlationId, payload);
    }

    private static void validateVersion(int version) {
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported wire version: " + version);
        }
    }
}
