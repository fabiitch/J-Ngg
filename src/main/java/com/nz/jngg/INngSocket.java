package com.nz.jngg;

import java.nio.charset.StandardCharsets;

public interface INngSocket extends AutoCloseable {

    int listen(String address);

    int dial(String address);

    int send(byte[] payload);

    byte[] receive();

    default int send(String data) {
        send(data.getBytes(StandardCharsets.UTF_8));
    }

    default String receiveString() {
        return new String(
                receive(),
                StandardCharsets.UTF_8);
    }
}
