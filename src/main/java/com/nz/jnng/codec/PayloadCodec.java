package com.nz.jnng.codec;

/**
 * Application-provided serialization of one message type.
 *
 * <p>A Protobuf module can implement this interface without making J-NNG
 * depend on the Protobuf runtime.</p>
 */
public interface PayloadCodec<T> {

    byte[] encode(T value);

    T decode(byte[] payload);
}
