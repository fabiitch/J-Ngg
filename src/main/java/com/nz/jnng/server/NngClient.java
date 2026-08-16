package com.nz.jnng.server;

import com.nz.jnng.ConnectionMode;

/** Dialing facade that owns every channel it creates. */
public final class NngClient extends AbstractNngEndpoint implements INngClient {
    public NngClient(String baseAddress, NngChannelFactory channelFactory) {
        super(baseAddress, channelFactory, ConnectionMode.DIAL);
    }
}
