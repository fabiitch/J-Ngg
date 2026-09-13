package com.fabiitch.jnng.socket;

/**
 * Process-wide NNG worker-pool configuration.
 *
 * <p>NNG initializes these pools once per process, so every {@code Jnng}
 * instance in the same JVM must use the same values.</p>
 */
public record NngRuntimeConfig(
        int taskThreads,
        int expireThreads,
        int pollerThreads,
        int resolverThreads
) {
    public NngRuntimeConfig {
        requireRange(taskThreads, 2, "taskThreads");
        requireRange(expireThreads, 1, "expireThreads");
        requireRange(pollerThreads, 1, "pollerThreads");
        requireRange(resolverThreads, 1, "resolverThreads");
    }

    /** Low-resource defaults suitable for a small local IPC topology. */
    public static NngRuntimeConfig defaults() {
        return new NngRuntimeConfig(2, 1, 1, 1);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static void requireRange(int value, int minimum, String name) {
        if (value < minimum || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be between "
                    + minimum + " and " + Short.MAX_VALUE);
        }
    }

    public static final class Builder {
        private int taskThreads = 2;
        private int expireThreads = 1;
        private int pollerThreads = 1;
        private int resolverThreads = 1;

        private Builder() {
        }

        public Builder taskThreads(int value) {
            taskThreads = value;
            return this;
        }

        public Builder expireThreads(int value) {
            expireThreads = value;
            return this;
        }

        public Builder pollerThreads(int value) {
            pollerThreads = value;
            return this;
        }

        public Builder resolverThreads(int value) {
            resolverThreads = value;
            return this;
        }

        public NngRuntimeConfig build() {
            return new NngRuntimeConfig(
                    taskThreads, expireThreads, pollerThreads, resolverThreads);
        }
    }
}
