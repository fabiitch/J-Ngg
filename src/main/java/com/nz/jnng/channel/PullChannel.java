package com.nz.jnng.channel;

import com.nz.jnng.Subscription;
import com.nz.jnng.codec.WireCodec;
import com.nz.jnng.message.NativeWireMessage;
import com.nz.jnng.message.WireMessage;
import com.nz.jnng.socket.INngSocket;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class PullChannel extends AbstractChannel {
    public PullChannel(INngSocket socket, WireCodec codec) { super(socket, codec); }
    public WireMessage pull() { return receiveMessage(); }
    public Optional<WireMessage> pull(Duration timeout) { return receiveMessage(timeout); }
    public Optional<WireMessage> tryPull() { return tryReceiveMessage(); }
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
}
