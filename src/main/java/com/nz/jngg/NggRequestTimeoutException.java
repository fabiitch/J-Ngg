package com.nz.jngg;

public final class NggRequestTimeoutException extends RuntimeException {
    public NggRequestTimeoutException() {
        super("Request timed out before it could be completed");
    }
}
