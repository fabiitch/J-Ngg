package com.fabiitch.jnng.service.listener;

@FunctionalInterface
public interface ChannelConnectionListener {
    void onConnectionChanged(ChannelConnectionEvent event);
}
