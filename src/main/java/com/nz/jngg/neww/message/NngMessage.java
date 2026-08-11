package com.nz.jngg.neww.message;


public record NngMessage(
        int version,
        String messageType,
        long messageId,
        byte[] payload
) {
}