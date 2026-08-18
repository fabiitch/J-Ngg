package com.nz.jnng.neww.codec;

/** Encodes and decodes one application message type. */
public interface ChannelMessageCodec<T> {
    byte[] encode(T message);

    T decode(byte[] payload);
}
