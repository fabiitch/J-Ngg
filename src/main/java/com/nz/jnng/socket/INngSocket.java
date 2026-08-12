package com.nz.jnng.socket;

import com.nz.jnng.message.NngReceiveResult;

import java.time.Duration;

public interface INngSocket extends AutoCloseable {

    int listen(String address);

    int dial(String address);

    int send(byte[] payload);

    int trySend(byte[] payload);

    NngReceiveResult receive();

    NngReceiveResult receive(Duration timeout);

    NngReceiveResult tryReceive();

    @Override
    void close();
}