package com.nz.jnng.neww.communication;

import com.nz.jnng.neww.AbstractReceivingChannel;
import com.nz.jnng.neww.ChannelConfiguration;
import com.nz.jnng.socket.INngSocket;
import com.nz.jnng.socket.impl.SubSocket;

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
