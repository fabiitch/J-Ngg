package com.nz.jnng.socket.impl;

import com.nz.jnng.socket.AbstractNngSocket;
import com.nz.jnng.nng_h;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

import com.nz.jnng.utils.Nng;
import com.nz.jnng.socket.NngSocketConfig;

public final class SubSocket extends AbstractNngSocket {
    public SubSocket() { super(); }
    public SubSocket(NngSocketConfig config) { super(config); }

    @Override
    protected int open(MemorySegment socket) {
        return nng_h.nng_sub0_open(socket);
    }

    public void subscribe(byte[] prefix) {
        Objects.requireNonNull(prefix, "prefix");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment bytes = prefix.length == 0
                    ? MemorySegment.NULL
                    : arena.allocateFrom(ValueLayout.JAVA_BYTE, prefix);
            Nng.check(nng_h.nng_sub0_socket_subscribe(getSocket(), bytes, prefix.length));
        }
    }

    public void unsubscribe(byte[] prefix) {
        Objects.requireNonNull(prefix, "prefix");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment bytes = prefix.length == 0
                    ? MemorySegment.NULL
                    : arena.allocateFrom(ValueLayout.JAVA_BYTE, prefix);
            Nng.check(nng_h.nng_sub0_socket_unsubscribe(getSocket(), bytes, prefix.length));
        }
    }
}
