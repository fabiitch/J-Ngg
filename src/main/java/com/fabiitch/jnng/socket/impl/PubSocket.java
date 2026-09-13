package com.fabiitch.jnng.socket.impl;


import com.fabiitch.jnng.socket.AbstractNngSocket;
import com.fabiitch.jnng.nng_h;

import java.lang.foreign.MemorySegment;
import com.fabiitch.jnng.socket.NngSocketConfig;

public final class PubSocket extends AbstractNngSocket {
    public PubSocket() { super(); }
    public PubSocket(NngSocketConfig config) { super(config); }

    @Override
    protected int open(MemorySegment socket) {
        return nng_h.nng_pub0_open(socket);
    }
}
