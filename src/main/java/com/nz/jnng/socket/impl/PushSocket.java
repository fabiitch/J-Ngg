package com.nz.jnng.socket.impl;

import com.nz.jnng.socket.AbstractNngSocket;
import com.nz.jnng.nng_h;

import java.lang.foreign.MemorySegment;
import com.nz.jnng.socket.NngSocketConfig;

public final class PushSocket extends AbstractNngSocket {
    public PushSocket() { super(); }
    public PushSocket(NngSocketConfig config) { super(config); }

    @Override
    protected int open(MemorySegment socket) {
     return nng_h.nng_push0_open(socket);
    }
}
