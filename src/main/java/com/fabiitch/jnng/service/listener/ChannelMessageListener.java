package com.fabiitch.jnng.service.listener;

@FunctionalInterface
public interface ChannelMessageListener<T> {
    void onMessage(T message);
}
