package com.nz.jngg;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class NggWireCodec {
    private NggWireCodec() {
    }

    static byte[] encode(short messageType, long requestId, byte[] payload) {
        NggHeader header = new NggHeader(payload.length, messageType, requestId);
        ByteBuffer buffer = ByteBuffer.allocate(NggHeader.BYTE_SIZE + payload.length)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(header.payloadLength());
        buffer.putShort(header.messageType());
        buffer.putLong(header.requestId());
        buffer.put(payload);
        return buffer.array();
    }

    static NggMessage decode(byte[] bytes) {
        if (bytes.length < NggHeader.BYTE_SIZE) {
            throw new IllegalArgumentException(
                    "Message is shorter than the " + NggHeader.BYTE_SIZE + " byte header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        NggHeader header = new NggHeader(buffer.getInt(), buffer.getShort(), buffer.getLong());
        if (header.payloadLength() != buffer.remaining()) {
            throw new IllegalArgumentException("Invalid payload length: header="
                    + header.payloadLength() + ", actual=" + buffer.remaining());
        }

        byte[] payload = new byte[header.payloadLength()];
        buffer.get(payload);
        return new NggMessage(header, payload);
    }
}
