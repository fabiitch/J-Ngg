package com.nz.jnng;

import com.nz.jnng.message.NngReceiveResult;
import com.nz.jnng.socket.INngSocket;
import com.nz.jnng.utils.Nng;
import com.nz.jnng.constants.NngErrorCode;
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
        return send(payload, 0);
    }

    @Override
    public int trySend(byte[] payload) {
        return send(payload, NngFlags.NONBLOCK);
    }

    private int send(byte[] payload, int flags) {
        try (Arena local = Arena.ofConfined()) {
            MemorySegment msgPtr =
                    local.allocate(ValueLayout.ADDRESS);

            int rc = nng_h.nng_msg_alloc(msgPtr, 0);

            if (rc != NngErrorCode.OK) {
                return rc;
            }

            MemorySegment msg =
                    msgPtr.get(ValueLayout.ADDRESS, 0);

            try {
                MemorySegment data =
                        local.allocateFrom(
                                ValueLayout.JAVA_BYTE,
                                payload
                        );

                rc = nng_h.nng_msg_append(
                        msg,
                        data,
                        payload.length
                );

                if (rc != NngErrorCode.OK) {
                    return rc;
                }

                rc = nng_h.nng_sendmsg(
                        socket,
                        msg,
                        flags
                );

                if (rc == NngErrorCode.OK) {
                    // Ownership transferred to NNG.
                    msg = MemorySegment.NULL;
                }

                return rc;

            } finally {
                if (!msg.equals(MemorySegment.NULL)) {
                    nng_h.nng_msg_free(msg);
                }
            }
        }
    }

    @Override
    public NngReceiveResult receive() {
        return receive(0);
    }

    @Override
    public NngReceiveResult tryReceive() {
        return receive(NngFlags.NONBLOCK);
    }

    private NngReceiveResult receive(int flags) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment msgPtr = arena.allocate(ValueLayout.ADDRESS);

            int rc = nng_h.nng_recvmsg(
                    socket,
                    msgPtr,
                    flags
            );

            if (rc != NngErrorCode.OK) {
                return new NngReceiveResult(rc, null);
            }

            MemorySegment msg =
                    msgPtr.get(ValueLayout.ADDRESS, 0);

            try {
                long len = nng_h.nng_msg_len(msg);
                MemorySegment body = nng_h.nng_msg_body(msg);

                byte[] data = body
                        .reinterpret(len)
                        .toArray(ValueLayout.JAVA_BYTE);

                return new NngReceiveResult(
                        NngErrorCode.OK,
                        data
                );

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
