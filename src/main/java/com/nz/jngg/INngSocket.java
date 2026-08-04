package com.nz.jngg;

import java.nio.charset.StandardCharsets;

public interface INngSocket extends AutoCloseable {

    void listen(String address);

    void dial(String address);

    void send(byte[] payload);

    byte[] receive();

    default void send(String data) {
        send(data.getBytes(StandardCharsets.UTF_8));
    }

    default String receiveString() {
        return new String(
                receive(),
                StandardCharsets.UTF_8);
    }
}
