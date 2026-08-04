package com.nz.jngg;

import com.nz.jngg.utils.Nng;
import com.nz.jnng.nng_h;

import java.lang.foreign.MemorySegment;

public final class ReqSocket extends AbstractNngSocket {

    @Override
    protected void open(MemorySegment socket) {
        Nng.check(nng_h.nng_req0_open(socket));
    }
}
