package com.nz.jnng.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

final class WireProtocol {
    static final int VERSION = 1;
    static final int HEADER_SIZE = Integer.BYTES * 3 + Long.BYTES * 2;

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
}
