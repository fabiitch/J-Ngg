package com.nz.jnng.codec;

import com.nz.jnng.message.NngMessage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class BinaryNngMessageCodec implements NngMessageCodec {

    @Override
    public byte[] encode(NngMessage message) {
        byte[] messageTypeBytes =
                message.messageType().getBytes(StandardCharsets.UTF_8);

        byte[] payload = message.payload();

        int totalLength =
                Integer.BYTES +                 // version
                        Integer.BYTES +                 // messageType length
                        messageTypeBytes.length +
                        Long.BYTES +                    // messageId
                        Integer.BYTES +                 // payload length
                        payload.length;

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);

        buffer.putInt(message.version());

        buffer.putInt(messageTypeBytes.length);
        buffer.put(messageTypeBytes);

        buffer.putLong(message.messageId());

        buffer.putInt(payload.length);
        buffer.put(payload);

        return buffer.array();
    }

    @Override
    public NngMessage decode(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        int version = buffer.getInt();

        int messageTypeLength = buffer.getInt();

        byte[] messageTypeBytes = new byte[messageTypeLength];
        buffer.get(messageTypeBytes);

        String messageType =
                new String(messageTypeBytes, StandardCharsets.UTF_8);

        long messageId = buffer.getLong();

        int payloadLength = buffer.getInt();

        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        return new NngMessage(
                version,
                messageType,
                messageId,
                payload
        );
    }
}