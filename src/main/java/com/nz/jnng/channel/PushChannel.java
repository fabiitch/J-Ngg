package com.nz.jnng.channel;

import com.nz.jnng.codec.WireCodec;
import com.nz.jnng.message.WireMessage;
import com.nz.jnng.socket.INngSocket;

import java.util.concurrent.CompletableFuture;

public final class PushChannel extends AbstractChannel {
    public PushChannel(INngSocket socket, WireCodec codec) { super(socket, codec); }
    public void push(WireMessage message) { sendMessage(message); }
    public boolean tryPush(WireMessage message) { return trySendMessage(message); }
    public CompletableFuture<Void> pushAsync(WireMessage message) {
        return sendMessageAsync(message);
    }
}
