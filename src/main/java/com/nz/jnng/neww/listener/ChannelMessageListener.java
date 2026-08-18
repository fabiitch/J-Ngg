package com.nz.jnng.neww.listener;

@FunctionalInterface
public interface ChannelMessageListener<T> {
    void onMessage(T message);
}
