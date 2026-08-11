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

    protected abstract int open(MemorySegment socket);

    @Override
    public int listen(String address) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment addr = arena.allocateFrom(address);

            return nng_h.nng_listen(
                    socket,
                    addr,
                    MemorySegment.NULL,
                    0
            );
        }
    }

    public int dial(String address) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment addr = arena.allocateFrom(address);
            return nng_h.nng_dial(
                    socket,
                    addr,
                    MemorySegment.NULL,
                    0
            );
        }
    }

    @Override
    public int send(byte[] payload) {
        try (Arena local = Arena.ofConfined()) {

            MemorySegment msgPtr = local.allocate(ValueLayout.ADDRESS);
            Nng.check(nng_h.nng_msg_alloc(msgPtr, 0));
            MemorySegment msg = msgPtr.get(ValueLayout.ADDRESS, 0);

            MemorySegment data = local.allocateFrom(ValueLayout.JAVA_BYTE, payload);
            Nng.check(nng_h.nng_msg_append(msg, data, payload.length));

            int result = nng_h.nng_sendmsg(socket, msg, 0);
            if (result == 0) {
                // NNG owns the message from now on.
                msg = MemorySegment.NULL;
            }
            return result;
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
