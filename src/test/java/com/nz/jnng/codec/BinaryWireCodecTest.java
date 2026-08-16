package com.nz.jnng.codec;

import com.nz.jnng.message.WireMessage;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BinaryWireCodecTest {

    private final BinaryWireCodec codec = new BinaryWireCodec();

    @Test
    void roundTripsEveryEnvelopeField() {
        WireMessage message = new WireMessage(
                1,
                42,
                123456789L,
                987654321L,
                new byte[]{1, 2, 3, 4}
        );

        WireMessage decoded = codec.decode(codec.encode(message));

        assertEquals(message.wireVersion(), decoded.wireVersion());
        assertEquals(message.messageTypeId(), decoded.messageTypeId());
        assertEquals(message.messageId(), decoded.messageId());
        assertEquals(message.correlationId(), decoded.correlationId());
        assertArrayEquals(message.payload(), decoded.payload());
    }

    @Test
    void writesTheHeaderInNetworkByteOrder() {
        byte[] encoded = codec.encode(new WireMessage(
                0x01020304,
                0x05060708,
                0x0102030405060708L,
                0x1112131415161718L,
                new byte[]{9, 10}
        ));

        ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        assertEquals(0x01020304, buffer.getInt());
        assertEquals(0x05060708, buffer.getInt());
        assertEquals(0x0102030405060708L, buffer.getLong());
        assertEquals(0x1112131415161718L, buffer.getLong());
        assertEquals(2, buffer.getInt());
        assertEquals(9, buffer.get());
        assertEquals(10, buffer.get());
    }

    @Test
    void rejectsTruncatedHeaders() {
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(new byte[BinaryWireCodec.HEADER_SIZE - 1]));
    }

    @Test
    void rejectsPayloadLengthMismatch() {
        byte[] encoded = codec.encode(new WireMessage(1, 42, 1, 0, new byte[]{1, 2}));
        ByteBuffer.wrap(encoded)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(BinaryWireCodec.HEADER_SIZE - Integer.BYTES, 3);

        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded));
    }
}
