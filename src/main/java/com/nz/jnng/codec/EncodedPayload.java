package com.nz.jnng.codec;

import java.util.Objects;

/** Application payload paired with its stable wire type id. */
public record EncodedPayload(int messageTypeId, byte[] bytes) {
    public EncodedPayload {
        if (messageTypeId <= 0) throw new IllegalArgumentException("messageTypeId must be > 0");
        Objects.requireNonNull(bytes, "bytes");
    }
}
