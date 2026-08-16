package com.nz.jnng.channel;

import com.nz.jnng.codec.WireCodec;
import com.nz.jnng.message.WireMessage;
import com.nz.jnng.socket.INngSocket;

import java.util.concurrent.CompletableFuture;

public final class PublisherChannel extends AbstractChannel {
    public PublisherChannel(INngSocket socket, WireCodec codec) { super(socket, codec); }
    public void publish(WireMessage message) { sendMessage(message); }
    public boolean tryPublish(WireMessage message) { return trySendMessage(message); }
    public CompletableFuture<Void> publishAsync(WireMessage message) {
        return sendMessageAsync(message);
    }
}
