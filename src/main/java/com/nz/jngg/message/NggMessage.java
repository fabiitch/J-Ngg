package com.nz.jngg.message;

import java.util.Arrays;
import java.util.Objects;

public record NggMessage(NggHeader header, byte[] payload) {
    public NggMessage {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(payload, "payload");
        if (header.payloadLength() != payload.length) {
            throw new IllegalArgumentException("Header payloadLength does not match payload length");
        }
        payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
