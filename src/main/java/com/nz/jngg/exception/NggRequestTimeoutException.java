package com.nz.jngg.exception;

public final class NggRequestTimeoutException extends RuntimeException {
    public NggRequestTimeoutException() {
        super("Request timed out before it could be completed");
    }
}
