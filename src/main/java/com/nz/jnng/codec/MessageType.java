package com.nz.jnng.codec;

import java.util.Objects;

/** Associates a stable wire id with a Java type and its payload codec. */
public record MessageType<T>(
        int id,
        Class<T> javaType,
        PayloadCodec<T> codec
) {
    public MessageType {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be > 0");
        }
        Objects.requireNonNull(javaType, "javaType");
        Objects.requireNonNull(codec, "codec");
    }
}
