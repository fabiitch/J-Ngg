package com.fabiitch.jnng.service.communication;

import com.fabiitch.jnng.service.channel.AbstractReceivingChannel;
import com.fabiitch.jnng.service.channel.ChannelConfiguration;
import com.fabiitch.jnng.socket.INngSocket;
import com.fabiitch.jnng.socket.impl.SubSocket;

import java.util.concurrent.Executor;

/** One-way SUB channel with application message dispatch. */
public final class SubChannel extends AbstractReceivingChannel {
    public SubChannel(ChannelConfiguration configuration, Executor executor) {
        super(configuration, executor, () -> new SubSocket(configuration.socketConfig()));
    }

    @Override
    protected void configureBeforeConnect(INngSocket socket) {
        ((SubSocket) socket).subscribe(new byte[0]);
    }
}
