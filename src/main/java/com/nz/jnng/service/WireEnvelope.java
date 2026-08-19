package com.nz.jnng.service;

import java.util.Objects;

record WireEnvelope(int version, int messageTypeId, long messageId,
                    long correlationId, byte[] payload) {
    WireEnvelope {
        if (version <= 0) throw new IllegalArgumentException("version must be > 0");
        if (messageTypeId <= 0) throw new IllegalArgumentException("messageTypeId must be > 0");
        Objects.requireNonNull(payload, "payload");
    }
}
