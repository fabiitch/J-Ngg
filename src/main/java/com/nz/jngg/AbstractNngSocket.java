package com.nz.jngg;

import com.nz.jngg.utils.Nng;
import com.nz.jnng.nng_h;
import com.nz.jnng.nng_socket;
import lombok.Getter;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public abstract class AbstractNngSocket implements INngSocket {

    protected final Arena arena;
    @Getter
    protected final MemorySegment socket;

    protected AbstractNngSocket() {
        this.arena = Arena.ofShared();
        this.socket = arena.allocate(nng_socket.layout());
        open(socket);
    }

    protected abstract void open(MemorySegment socket);

    @Override
    public void listen(String address) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment addr = arena.allocateFrom(address);
            Nng.check(nng_h.nng_listen(socket, addr, MemorySegment.NULL, 0));
        }
    }

    @Override
    public void dial(String address) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment addr = arena.allocateFrom(address);
            Nng.check(nng_h.nng_dial(socket, addr, MemorySegment.NULL, 0));
        }
    }

    @Override
    public void send(byte[] payload) {
        try (Arena local = Arena.ofConfined()) {
            MemorySegment msgPtr = local.allocate(ValueLayout.ADDRESS);
            Nng.check(nng_h.nng_msg_alloc(msgPtr, 0));
            MemorySegment msg = msgPtr.get(ValueLayout.ADDRESS, 0);
            try {
                MemorySegment data = local.allocateFrom(ValueLayout.JAVA_BYTE, payload);
                Nng.check(nng_h.nng_msg_append(msg, data, payload.length));
                Nng.check(nng_h.nng_sendmsg(socket, msg, 0));
            // ownership transféré à NNG
                msg = MemorySegment.NULL;
            } finally {
                if (!msg.equals(MemorySegment.NULL)) {
                    nng_h.nng_msg_free(msg);
                }
            }
        }
    }

    @Override
    public byte[] receive() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment msgPtr = arena.allocate(ValueLayout.ADDRESS);
            Nng.check(nng_h.nng_recvmsg(socket, msgPtr, 0));
            MemorySegment msg = msgPtr.get(ValueLayout.ADDRESS, 0);
            try {
                long len = nng_h.nng_msg_len(msg);
                MemorySegment body = nng_h.nng_msg_body(msg);
                return body.reinterpret(len).toArray(ValueLayout.JAVA_BYTE);
            } finally {
                nng_h.nng_msg_free(msg);
            }
        }
    }

    @Override
    public void close() {
        Nng.check(nng_h.nng_socket_close(socket));
        arena.close();
    }

}
