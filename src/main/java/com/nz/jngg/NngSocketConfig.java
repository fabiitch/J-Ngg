package com.nz.jngg;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record NngSocketConfig(
        Optional<Duration> requestTimeout,
        int maxPendingRequestsPerPeer,
        Duration reconnectInterval
) {
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    public static final int DEFAULT_MAX_PENDING_REQUESTS_PER_PEER = 64;
    public static final Duration DEFAULT_RECONNECT_INTERVAL = Duration.ofSeconds(2);

    public NngSocketConfig {
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        requestTimeout.ifPresent(timeout -> requirePositive(timeout, "requestTimeout"));
        if (maxPendingRequestsPerPeer <= 0) {
            throw new IllegalArgumentException("maxPendingRequestsPerPeer must be > 0");
        }
        requirePositive(reconnectInterval, "reconnectInterval");
        toNngMillis(reconnectInterval, "reconnectInterval");
        requestTimeout.ifPresent(timeout -> toNngMillis(timeout, "requestTimeout"));
    }

    public static NngSocketConfig defaults() {
        return new NngSocketConfig(Optional.of(DEFAULT_REQUEST_TIMEOUT),
                DEFAULT_MAX_PENDING_REQUESTS_PER_PEER, DEFAULT_RECONNECT_INTERVAL);
    }

    public NngSocketConfig withRequestTimeout(Duration timeout) {
        return new NngSocketConfig(Optional.of(timeout), maxPendingRequestsPerPeer, reconnectInterval);
    }

    public NngSocketConfig withInfiniteRequestTimeout() {
        return new NngSocketConfig(Optional.empty(), maxPendingRequestsPerPeer, reconnectInterval);
    }

    public NngSocketConfig withMaxPendingRequestsPerPeer(int maximum) {
        return new NngSocketConfig(requestTimeout, maximum, reconnectInterval);
    }

    public NngSocketConfig withReconnectInterval(Duration interval) {
        return new NngSocketConfig(requestTimeout, maxPendingRequestsPerPeer, interval);
    }

    int requestTimeoutMillis() {
        return requestTimeout.map(timeout -> toNngMillis(timeout, "requestTimeout")).orElse(-1);
    }

    int reconnectIntervalMillis() {
        return toNngMillis(reconnectInterval, "reconnectInterval");
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
    }

    private static int toNngMillis(Duration duration, String name) {
        long millis;
        try {
            millis = duration.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " is too large", exception);
        }
        if (millis <= 0 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be between 1 ms and "
                    + Integer.MAX_VALUE + " ms");
        }
        return (int) millis;
    }
}
