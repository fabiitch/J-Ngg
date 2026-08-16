package com.nz.jnng.message;

import java.util.Objects;

/**
 * Transport envelope exchanged between J-NNG peers.
 *
 * <p>The payload remains serialization-agnostic. Its meaning is defined by the
 * {@code MessageType} registered for {@link #messageTypeId()}.</p>
 */
public record WireMessage(
        int wireVersion,
        int messageTypeId,
        long messageId,
        long correlationId,
        byte[] payload
) {
    public WireMessage {
        if (wireVersion <= 0) {
            throw new IllegalArgumentException("wireVersion must be > 0");
        }
        if (messageTypeId <= 0) {
            throw new IllegalArgumentException("messageTypeId must be > 0");
        }
        Objects.requireNonNull(payload, "payload");
    }
}
