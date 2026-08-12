package com.nz.jnng.socket.impl;

import com.nz.jnng.AbstractNngSocket;
import com.nz.jnng.utils.Nng;
import com.nz.jnng.nng_h;

import java.lang.foreign.MemorySegment;

public final class PushSocket extends AbstractNngSocket {

    @Override
    protected int open(MemorySegment socket) {
     return nng_h.nng_push0_open(socket);
    }
}
