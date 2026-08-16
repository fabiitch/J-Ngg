package com.nz.jnng.server;

import com.nz.jnng.ConnectionMode;

/** Listening facade that owns every channel it creates. */
public final class NngServer extends AbstractNngEndpoint implements INngServer {
    public NngServer(String baseAddress, NngChannelFactory channelFactory) {
        super(baseAddress, channelFactory, ConnectionMode.LISTEN);
    }
}
