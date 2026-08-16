package com.nz.jnng.channel;

import com.nz.jnng.Subscription;
import com.nz.jnng.codec.WireCodec;
import com.nz.jnng.message.NativeWireMessage;
import com.nz.jnng.message.WireMessage;
import com.nz.jnng.socket.INngSocket;
import com.nz.jnng.socket.impl.SubSocket;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class SubscriberChannel extends AbstractChannel {
    public SubscriberChannel(INngSocket socket, WireCodec codec) { super(socket, codec); }
    public void subscribeTo(byte[] prefix) { subscriber().subscribe(prefix); }
    public void unsubscribeFrom(byte[] prefix) { subscriber().unsubscribe(prefix); }
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
    public Subscription onNativeMessage(
            Executor executor,
            Consumer<NativeWireMessage> listener
    ) {
        return subscribeNative(executor, listener);
    }

    private SubSocket subscriber() {
        if (socket instanceof SubSocket subscriber) return subscriber;
        throw new IllegalStateException("Underlying socket is not a SUB socket");
    }
}
