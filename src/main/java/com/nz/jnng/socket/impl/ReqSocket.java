package com.nz.jnng.socket.impl;

import com.nz.jnng.socket.AbstractNngSocket;
import com.nz.jnng.nng_h;

import java.lang.foreign.MemorySegment;
import com.nz.jnng.socket.NngSocketConfig;

public final class ReqSocket extends AbstractNngSocket {
    public ReqSocket() { super(); }
    public ReqSocket(NngSocketConfig config) { super(config); }

    @Override
    protected int open(MemorySegment socket) {
        return nng_h.nng_req0_open(socket);
    }
}
