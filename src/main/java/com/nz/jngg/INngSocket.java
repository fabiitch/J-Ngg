package com.nz.jngg;

public interface NngSocket extends AutoCloseable {

    void listen(String address);

    void dial(String address);

    void send(byte[] payload);

    byte[] receive();
}
