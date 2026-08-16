package com.nz.jnng.socket;

import com.nz.jnng.message.NngReceiveResult;
import com.nz.jnng.message.NngNativeReceiveResult;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public interface INngSocket extends AutoCloseable {

    int listen(String address);

    int dial(String address);

    int send(byte[] payload);

    int trySend(byte[] payload);

    int send(NativeMessage message);

    CompletableFuture<Void> sendAsync(byte[] payload);

    NngSocketConfig config();

    NngReceiveResult receive();

    NngReceiveResult receive(Duration timeout);

    NngReceiveResult tryReceive();

    NngNativeReceiveResult receiveNative();

    NngNativeReceiveResult tryReceiveNative();

    CompletableFuture<NativeMessage> receiveNativeAsync();

    CompletableFuture<NativeMessage> receiveNativeAsync(Duration timeout);

    @Override
    void close();
}
