package com.nz.jnng.codec;

import com.nz.jnng.message.WireMessage;
import com.nz.jnng.message.NativeWireMessage;
import com.nz.jnng.socket.NativeMessage;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Binary, language-neutral encoding of a {@link WireMessage}.
 *
 * <p>All numeric fields use network byte order (big endian).</p>
 */
public final class BinaryWireCodec implements WireCodec {

    private static final ValueLayout.OfInt NETWORK_INT =
            ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong NETWORK_LONG =
            ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    public static final int HEADER_SIZE =
            Integer.BYTES     // wire version
                    + Integer.BYTES // message type id
                    + Long.BYTES    // message id
                    + Long.BYTES    // correlation id
                    + Integer.BYTES; // payload length

    @Override
    public byte[] encode(WireMessage message) {
        Objects.requireNonNull(message, "message");
        byte[] payload = message.payload();
        int totalLength = Math.addExact(HEADER_SIZE, payload.length);

        return ByteBuffer.allocate(totalLength)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(message.wireVersion())
                .putInt(message.messageTypeId())
                .putLong(message.messageId())
                .putLong(message.correlationId())
                .putInt(payload.length)
                .put(payload)
                .array();
    }

    @Override
    public NativeMessage encodeNative(WireMessage message) {
        Objects.requireNonNull(message, "message");
        byte[] payload = message.payload();
        NativeMessage nativeMessage = NativeMessage.allocate(
                Math.addExact(HEADER_SIZE, payload.length));
        try {
            MemorySegment body = nativeMessage.body();
            body.set(NETWORK_INT, 0, message.wireVersion());
            body.set(NETWORK_INT, Integer.BYTES, message.messageTypeId());
            body.set(NETWORK_LONG, 2L * Integer.BYTES, message.messageId());
            body.set(NETWORK_LONG, 2L * Integer.BYTES + Long.BYTES,
                    message.correlationId());
            body.set(NETWORK_INT, HEADER_SIZE - Integer.BYTES, payload.length);
            if (payload.length > 0) {
                MemorySegment.copy(payload, 0, body, ValueLayout.JAVA_BYTE,
                        HEADER_SIZE, payload.length);
            }
            return nativeMessage;
        } catch (Throwable error) {
            nativeMessage.close();
            throw error;
        }
    }

    @Override
    public WireMessage decode(byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length < HEADER_SIZE) {
            throw new IllegalArgumentException(
                    "Message is shorter than the " + HEADER_SIZE + " byte wire header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int wireVersion = buffer.getInt();
        int messageTypeId = buffer.getInt();
        long messageId = buffer.getLong();
        long correlationId = buffer.getLong();
        int payloadLength = buffer.getInt();

        if (payloadLength < 0 || payloadLength != buffer.remaining()) {
            throw new IllegalArgumentException("Invalid payload length: header="
                    + payloadLength + ", actual=" + buffer.remaining());
        }

        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        return new WireMessage(
                wireVersion,
                messageTypeId,
                messageId,
                correlationId,
                payload
        );
    }

    @Override
    public NativeWireMessage decodeNative(NativeMessage message) {
        Objects.requireNonNull(message, "message");
        MemorySegment body = message.body();
        if (body.byteSize() < HEADER_SIZE) {
            throw new IllegalArgumentException(
                    "Message is shorter than the " + HEADER_SIZE + " byte wire header");
        }

        int wireVersion = body.get(NETWORK_INT, 0);
        int messageTypeId = body.get(NETWORK_INT, Integer.BYTES);
        long messageId = body.get(NETWORK_LONG, 2L * Integer.BYTES);
        long correlationId = body.get(
                NETWORK_LONG,
                2L * Integer.BYTES + Long.BYTES
        );
        int payloadLength = body.get(NETWORK_INT, HEADER_SIZE - Integer.BYTES);
        long actualPayloadLength = body.byteSize() - HEADER_SIZE;
        if (payloadLength < 0 || payloadLength != actualPayloadLength) {
            throw new IllegalArgumentException("Invalid payload length: header="
                    + payloadLength + ", actual=" + actualPayloadLength);
        }
        if (wireVersion <= 0 || messageTypeId <= 0) {
            throw new IllegalArgumentException("Invalid wire version or message type id");
        }

        return new NativeWireMessage(
                message,
                wireVersion,
                messageTypeId,
                messageId,
                correlationId,
                body.asSlice(HEADER_SIZE, payloadLength)
        );
    }
}
