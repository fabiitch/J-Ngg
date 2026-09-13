package com.fabiitch.jnng.service.channel;

import com.fabiitch.jnng.ConnectionMode;
import com.fabiitch.jnng.socket.NngSocketConfig;

import java.util.Objects;

/** Immutable transport configuration shared by every concrete channel type. */
public record ChannelConfiguration(
        String address,
        ConnectionMode connectionMode,
        NngSocketConfig socketConfig,
        ChannelQueuePolicy queuePolicy
) {
    public ChannelConfiguration {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(connectionMode, "connectionMode");
        Objects.requireNonNull(socketConfig, "socketConfig");
        Objects.requireNonNull(queuePolicy, "queuePolicy");
        if (address.isBlank()) {
            throw new IllegalArgumentException("address must not be blank");
        }
    }

    /** Backward-compatible constructor using the default bounded queue policy. */
    public ChannelConfiguration(
            String address,
            ConnectionMode connectionMode,
            NngSocketConfig socketConfig
    ) {
        this(address, connectionMode, socketConfig, ChannelQueuePolicy.defaults());
    }

    public static Builder listen(String address) {
        return new Builder(address, ConnectionMode.LISTEN);
    }

    public static Builder dial(String address) {
        return new Builder(address, ConnectionMode.DIAL);
    }

    public static final class Builder {
        private final String address;
        private final ConnectionMode connectionMode;
        private NngSocketConfig socketConfig = NngSocketConfig.defaults();
        private ChannelQueuePolicy queuePolicy = ChannelQueuePolicy.defaults();

        private Builder(String address, ConnectionMode connectionMode) {
            this.address = Objects.requireNonNull(address, "address");
            this.connectionMode = Objects.requireNonNull(connectionMode, "connectionMode");
        }

        public Builder socketConfig(NngSocketConfig socketConfig) {
            this.socketConfig = Objects.requireNonNull(socketConfig, "socketConfig");
            return this;
        }

        public Builder queuePolicy(ChannelQueuePolicy queuePolicy) {
            this.queuePolicy = Objects.requireNonNull(queuePolicy, "queuePolicy");
            return this;
        }

        public ChannelConfiguration build() {
            return new ChannelConfiguration(address, connectionMode, socketConfig, queuePolicy);
        }
    }
}
