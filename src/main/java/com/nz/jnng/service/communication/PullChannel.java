package com.nz.jnng.service.communication;

import com.nz.jnng.service.AbstractReceivingChannel;
import com.nz.jnng.service.ChannelConfiguration;
import com.nz.jnng.socket.impl.PullSocket;

import java.util.concurrent.Executor;

/** One-way PULL work consumer. */
public final class PullChannel extends AbstractReceivingChannel {
    public PullChannel(ChannelConfiguration configuration, Executor executor) {
        super(configuration, executor, () -> new PullSocket(configuration.socketConfig()));
    }
}
