package com.nz.jnng.message;


public record NngMessage(
        int version,
        String messageType,
        long messageId,
        byte[] payload
) {
}