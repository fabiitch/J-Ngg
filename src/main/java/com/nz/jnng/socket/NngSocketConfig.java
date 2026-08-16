package com.nz.jnng.socket;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Immutable native socket and request timeout configuration. */
public record NngSocketConfig(
        Optional<Duration> sendTimeout,
        Optional<Duration> receiveTimeout,
        Optional<Duration> requestTimeout,
        Duration reconnectMin,
        Duration reconnectMax,
        long maxReceiveSize
) {
    public static final long DEFAULT_MAX_RECEIVE_SIZE = 16L * 1024 * 1024;

    public NngSocketConfig {
        Objects.requireNonNull(sendTimeout, "sendTimeout");
        Objects.requireNonNull(receiveTimeout, "receiveTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        Objects.requireNonNull(reconnectMin, "reconnectMin");
        Objects.requireNonNull(reconnectMax, "reconnectMax");
        sendTimeout.ifPresent(timeout -> requirePositive(timeout, "sendTimeout"));
        receiveTimeout.ifPresent(timeout -> requirePositive(timeout, "receiveTimeout"));
        requestTimeout.ifPresent(timeout -> requirePositive(timeout, "requestTimeout"));
        requirePositive(reconnectMin, "reconnectMin");
        requirePositive(reconnectMax, "reconnectMax");
        if (reconnectMax.compareTo(reconnectMin) < 0) {
            throw new IllegalArgumentException("reconnectMax must be >= reconnectMin");
        }
        if (maxReceiveSize <= 0) {
            throw new IllegalArgumentException("maxReceiveSize must be > 0");
        }
        sendTimeout.ifPresent(NngSocketConfig::toNngMillis);
        receiveTimeout.ifPresent(NngSocketConfig::toNngMillis);
        requestTimeout.ifPresent(NngSocketConfig::toNngMillis);
        toNngMillis(reconnectMin);
        toNngMillis(reconnectMax);
    }

    public static NngSocketConfig defaults() {
        return new NngSocketConfig(
                Optional.of(Duration.ofSeconds(30)),
                Optional.empty(),
                Optional.of(Duration.ofSeconds(30)),
                Duration.ofMillis(100),
                Duration.ofSeconds(1),
                DEFAULT_MAX_RECEIVE_SIZE
        );
    }

    public NngSocketConfig withSendTimeout(Duration timeout) {
        return new NngSocketConfig(Optional.of(timeout), receiveTimeout, requestTimeout,
                reconnectMin, reconnectMax, maxReceiveSize);
    }

    public NngSocketConfig withInfiniteSendTimeout() {
        return new NngSocketConfig(Optional.empty(), receiveTimeout, requestTimeout,
                reconnectMin, reconnectMax, maxReceiveSize);
    }

    public NngSocketConfig withReceiveTimeout(Duration timeout) {
        return new NngSocketConfig(sendTimeout, Optional.of(timeout), requestTimeout,
                reconnectMin, reconnectMax, maxReceiveSize);
    }

    public NngSocketConfig withInfiniteReceiveTimeout() {
        return new NngSocketConfig(sendTimeout, Optional.empty(), requestTimeout,
                reconnectMin, reconnectMax, maxReceiveSize);
    }

    public NngSocketConfig withRequestTimeout(Duration timeout) {
        return new NngSocketConfig(sendTimeout, receiveTimeout, Optional.of(timeout),
                reconnectMin, reconnectMax, maxReceiveSize);
    }

    public NngSocketConfig withInfiniteRequestTimeout() {
        return new NngSocketConfig(sendTimeout, receiveTimeout, Optional.empty(),
                reconnectMin, reconnectMax, maxReceiveSize);
    }

    public NngSocketConfig withReconnect(Duration minimum, Duration maximum) {
        return new NngSocketConfig(sendTimeout, receiveTimeout, requestTimeout,
                minimum, maximum, maxReceiveSize);
    }

    public NngSocketConfig withMaxReceiveSize(long maximum) {
        return new NngSocketConfig(sendTimeout, receiveTimeout, requestTimeout,
                reconnectMin, reconnectMax, maximum);
    }

    int sendTimeoutMillis() {
        return sendTimeout.map(NngSocketConfig::toNngMillis).orElse(-1);
    }

    int receiveTimeoutMillis() {
        return receiveTimeout.map(NngSocketConfig::toNngMillis).orElse(-1);
    }

    int reconnectMinMillis() { return toNngMillis(reconnectMin); }
    int reconnectMaxMillis() { return toNngMillis(reconnectMax); }

    static int toNngMillis(Duration duration) {
        long millis;
        try {
            millis = duration.toMillis();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("Duration is too large for NNG", error);
        }
        if (millis <= 0 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Duration must be between 1 ms and " + Integer.MAX_VALUE + " ms");
        }
        return (int) millis;
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
    }
}
