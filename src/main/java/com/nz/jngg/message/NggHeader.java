package com.nz.jngg.message;

/** Header written before every application payload. */
public record NggHeader(int payloadLength, short messageType, long requestId) {
    public static final int BYTE_SIZE = Integer.BYTES + Short.BYTES + Long.BYTES;

    public NggHeader {
        if (payloadLength < 0) {
            throw new IllegalArgumentException("payloadLength must be >= 0");
        }
    }
}
