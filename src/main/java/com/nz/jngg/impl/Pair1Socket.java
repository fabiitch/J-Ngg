package com.nz.jngg.impl;

import com.nz.jngg.AbstractNngSocket;
import com.nz.jngg.utils.Nng;
import com.nz.jnng.nng_h;

import java.lang.foreign.MemorySegment;

public final class Pair1Socket extends AbstractNngSocket {

    @Override
    protected int open(MemorySegment socket) {
       return nng_h.nng_pair1_open(socket);
    }
}
