package com.nz.jnng.service.communication;

import com.nz.jnng.service.channel.AbstractReceivingChannel;
import com.nz.jnng.service.channel.ChannelConfiguration;
import com.nz.jnng.socket.impl.PullSocket;

import java.util.concurrent.Executor;

/** One-way PULL work consumer. */
public final class PullChannel extends AbstractReceivingChannel {
    public PullChannel(ChannelConfiguration configuration, Executor executor) {
        super(configuration, executor, () -> new PullSocket(configuration.socketConfig()));
    }
}
