package com.nz.jnng.socket.impl;

import com.nz.jnng.socket.AbstractNngSocket;
import com.nz.jnng.nng_h;

import java.lang.foreign.MemorySegment;
import com.nz.jnng.socket.NngSocketConfig;

public final class Pair1Socket extends AbstractNngSocket {
    public Pair1Socket() { super(); }
    public Pair1Socket(NngSocketConfig config) { super(config); }

    @Override
    protected int open(MemorySegment socket) {
       return nng_h.nng_pair1_open(socket);
    }
}
