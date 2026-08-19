package com.nz.jnng.service.communication;

import com.nz.jnng.service.AbstractChannel;
import com.nz.jnng.service.ChannelConfiguration;
import com.nz.jnng.socket.impl.PushSocket;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** One-way PUSH work distributor. */
public final class PushChannel extends AbstractChannel {
    public PushChannel(ChannelConfiguration configuration, Executor executor) {
        super(configuration, executor, () -> new PushSocket(configuration.socketConfig()));
    }

    public void push(Object message) { sendMessage(message); }
    public boolean tryPush(Object message) { return trySendMessage(message); }
    public CompletableFuture<Void> pushAsync(Object message) { return sendMessageAsync(message); }
}
