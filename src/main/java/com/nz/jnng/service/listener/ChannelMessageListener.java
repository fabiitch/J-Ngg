package com.nz.jnng.service.listener;

@FunctionalInterface
public interface ChannelMessageListener<T> {
    void onMessage(T message);
}
