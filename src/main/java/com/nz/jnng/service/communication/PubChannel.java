package com.nz.jnng.service.communication;

import com.nz.jnng.service.AbstractChannel;
import com.nz.jnng.service.ChannelConfiguration;
import com.nz.jnng.socket.impl.PubSocket;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** One-way PUB channel. */
public final class PubChannel extends AbstractChannel {
    public PubChannel(ChannelConfiguration configuration, Executor executor) {
        super(configuration, executor, () -> new PubSocket(configuration.socketConfig()));
    }

    public void publish(Object message) { sendMessage(message); }
    public boolean tryPublish(Object message) { return trySendMessage(message); }
    public CompletableFuture<Void> publishAsync(Object message) {
        return sendMessageAsync(message);
    }
}
