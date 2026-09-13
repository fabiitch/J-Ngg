package com.fabiitch.jnng.service.communication;

import com.fabiitch.jnng.service.channel.AbstractReceivingChannel;
import com.fabiitch.jnng.service.channel.ChannelConfiguration;
import com.fabiitch.jnng.socket.impl.Pair1Socket;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Bidirectional one-to-one PAIR channel. */
public final class PairChannel extends AbstractReceivingChannel {
    public PairChannel(ChannelConfiguration configuration, Executor executor) {
        super(configuration, executor, () -> new Pair1Socket(configuration.socketConfig()));
    }

    public void send(Object message) { sendMessage(message); }
    public boolean trySend(Object message) { return trySendMessage(message); }
    public CompletableFuture<Void> sendAsync(Object message) { return sendMessageAsync(message); }
}
