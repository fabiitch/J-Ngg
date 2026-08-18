package com.nz.jnng.neww.communication;

import com.nz.jnng.neww.AbstractReceivingChannel;
import com.nz.jnng.neww.ChannelConfiguration;
import com.nz.jnng.socket.impl.PullSocket;

import java.util.concurrent.Executor;

/** One-way PULL work consumer. */
public final class PullChannel extends AbstractReceivingChannel {
    public PullChannel(ChannelConfiguration configuration, Executor executor) {
        super(configuration, executor, () -> new PullSocket(configuration.socketConfig()));
    }
}
