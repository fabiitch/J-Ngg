package com.fabiitch.jnng.socket.impl;

import com.fabiitch.jnng.socket.AbstractNngSocket;
import com.fabiitch.jnng.nng_h;

import java.lang.foreign.MemorySegment;
import com.fabiitch.jnng.socket.NngSocketConfig;

public final class RepSocket extends AbstractNngSocket {
    public RepSocket() { super(); }
    public RepSocket(NngSocketConfig config) { super(config); }
    @Override
    protected int open(MemorySegment socket) {
        return nng_h.nng_rep0_open(socket);
    }
}
