package com.nz.jngg.impl;

import com.nz.jngg.AbstractNngSocket;
import com.nz.jngg.utils.Nng;
import com.nz.jnng.nng_h;

import java.lang.foreign.MemorySegment;

public final class SubSocket extends AbstractNngSocket {

    @Override
    protected void open(MemorySegment socket) {
        Nng.check(nng_h.nng_sub0_open(socket));
    }
}
