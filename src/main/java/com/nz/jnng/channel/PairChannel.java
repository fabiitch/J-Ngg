package com.nz.jnng.channel;

import com.nz.jnng.Subscription;
import com.nz.jnng.codec.WireCodec;
import com.nz.jnng.message.NativeWireMessage;
import com.nz.jnng.message.WireMessage;
import com.nz.jnng.socket.INngSocket;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Bidirectional one-to-one PAIR channel. */
public final class PairChannel extends AbstractChannel {
    public PairChannel(INngSocket socket, WireCodec codec) { super(socket, codec); }
    public void send(WireMessage message) { sendMessage(message); }
    public boolean trySend(WireMessage message) { return trySendMessage(message); }
    public CompletableFuture<Void> sendAsync(WireMessage message) {
        return sendMessageAsync(message);
    }
    public WireMessage receive() { return receiveMessage(); }
    public Optional<WireMessage> receive(Duration timeout) { return receiveMessage(timeout); }
    public Optional<WireMessage> tryReceive() { return tryReceiveMessage(); }
    public Subscription onMessage(Consumer<WireMessage> listener) { return subscribe(listener); }
    public Subscription onMessage(Executor executor, Consumer<WireMessage> listener) {
        return subscribe(executor, listener);
    }
    public Subscription onNativeMessage(Consumer<NativeWireMessage> listener) {
        return subscribeNative(listener);
    }
    public Subscription onNativeMessage(Executor executor, Consumer<NativeWireMessage> listener) {
        return subscribeNative(executor, listener);
    }
}
