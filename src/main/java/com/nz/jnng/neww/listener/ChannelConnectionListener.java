package com.nz.jnng.neww.listener;

@FunctionalInterface
public interface ChannelConnectionListener {
    void onConnectionChanged(ChannelConnectionEvent event);
}
