package com.nz.jngg;

public final class TooManyPendingRequestsException extends RuntimeException {
    public TooManyPendingRequestsException(int maximum) {
        super("Maximum pending requests reached for this peer: " + maximum);
    }
}
