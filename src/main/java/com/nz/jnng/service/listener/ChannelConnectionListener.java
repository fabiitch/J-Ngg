package com.nz.jnng.service.listener;

@FunctionalInterface
public interface ChannelConnectionListener {
    void onConnectionChanged(ChannelConnectionEvent event);
}
