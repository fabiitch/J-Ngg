package com.nz.jnng.socket;

import com.nz.jnng.Subscription;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface INngSocket extends AutoCloseable {

    int listen(String address);

    int dial(String address);

    int send(byte[] payload);

    int trySend(byte[] payload);

    CompletableFuture<Void> sendAsync(byte[] payload);

    NngSocketConfig config();

    CompletableFuture<NativeMessage> receiveNativeAsync();

    CompletableFuture<NativeMessage> receiveNativeAsync(Duration timeout);

    Subscription onConnectionChanged(Consumer<SocketConnectionEvent> listener);

    @Override
    void close();
}
