package com.nz.jnng.socket;

import com.nz.jnng.constants.NngFlags;
import com.nz.jnng.message.NngReceiveResult;
import com.nz.jnng.message.NngNativeReceiveResult;
import com.nz.jnng.nng_h;
import com.nz.jnng.nng_socket;
import com.nz.jnng.utils.Nng;
import com.nz.jnng.constants.NngErrorCode;
import lombok.Getter;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Optional;

public abstract class AbstractNngSocket implements INngSocket {

    protected final Arena arena;
    @Getter
    protected final MemorySegment socket;
    private final NngSocketConfig config;
    private final AtomicBoolean closed = new AtomicBoolean();

    protected AbstractNngSocket() {
        this(NngSocketConfig.defaults());
    }

    protected AbstractNngSocket(NngSocketConfig config) {
        Nng.load();
        this.config = Objects.requireNonNull(config, "config");
        this.arena = Arena.ofShared();
        this.socket = arena.allocate(nng_socket.layout());
        int rc = open(socket);
        if (rc != NngErrorCode.OK) {
            arena.close();
            throw new com.nz.jnng.exception.NngException(rc);
        }
        try {
            configure();
        } catch (Throwable error) {
            nng_h.nng_socket_close(socket);
            arena.close();
            throw error;
        }
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
                    NngFlags.NONBLOCK
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

    @Override
    public int send(NativeMessage message) {
        Objects.requireNonNull(message, "message");
        int rc = nng_h.nng_sendmsg(socket, message.handle(), NngFlags.NONE);
        if (rc == NngErrorCode.OK) {
            message.transferredToNng();
        }
        return rc;
    }

    @Override
    public CompletableFuture<Void> sendAsync(byte[] payload) {
        ensureOpen();
        return NngAio.send(socket, payload, config.sendTimeout());
    }

    @Override
    public NngSocketConfig config() {
        return config;
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
        return copy(receiveNative(NngFlags.NONE));
    }

    @Override
    public synchronized NngReceiveResult receive(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be >= 0");
        }
        if (timeout.isZero()) {
            NngReceiveResult result = copy(receiveNative(NngFlags.NONBLOCK));
            return result.code() == NngErrorCode.EAGAIN
                    ? new NngReceiveResult(NngErrorCode.ETIMEDOUT, null)
                    : result;
        }

        long timeoutMillis = Math.max(1, timeout.toMillis());
        if (timeoutMillis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("timeout must be <= "
                    + Integer.MAX_VALUE + " ms");
        }

        try (Arena local = Arena.ofConfined()) {
            MemorySegment previousTimeout = local.allocate(ValueLayout.JAVA_INT);
            int rc = nng_h.nng_socket_get_ms(
                    socket,
                    nng_h.NNG_OPT_RECVTIMEO(),
                    previousTimeout
            );
            if (rc != NngErrorCode.OK) {
                return new NngReceiveResult(rc, null);
            }

            rc = nng_h.nng_socket_set_ms(
                    socket,
                    nng_h.NNG_OPT_RECVTIMEO(),
                    (int) timeoutMillis
            );
            if (rc != NngErrorCode.OK) {
                return new NngReceiveResult(rc, null);
            }

            try {
                return copy(receiveNative(NngFlags.NONE));
            } finally {
                nng_h.nng_socket_set_ms(
                        socket,
                        nng_h.NNG_OPT_RECVTIMEO(),
                        previousTimeout.get(ValueLayout.JAVA_INT, 0)
                );
            }
        }
    }

    @Override
    public NngReceiveResult tryReceive() {
        return copy(receiveNative(NngFlags.NONBLOCK));
    }

    @Override
    public NngNativeReceiveResult receiveNative() {
        return receiveNative(NngFlags.NONE);
    }

    @Override
    public NngNativeReceiveResult tryReceiveNative() {
        return receiveNative(NngFlags.NONBLOCK);
    }

    @Override
    public CompletableFuture<NativeMessage> receiveNativeAsync() {
        ensureOpen();
        return NngAio.receive(socket, config.receiveTimeout());
    }

    @Override
    public CompletableFuture<NativeMessage> receiveNativeAsync(Duration timeout) {
        ensureOpen();
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be > 0");
        }
        return NngAio.receive(socket, Optional.of(timeout));
    }

    private NngNativeReceiveResult receiveNative(int flags) {
        ensureOpen();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment msgPtr = arena.allocate(ValueLayout.ADDRESS);

            int rc = nng_h.nng_recvmsg(
                    socket,
                    msgPtr,
                    flags
            );

            if (rc != NngErrorCode.OK) {
                return new NngNativeReceiveResult(rc, null);
            }

            MemorySegment msg =
                    msgPtr.get(ValueLayout.ADDRESS, 0);
            return new NngNativeReceiveResult(NngErrorCode.OK, NativeMessage.adopt(msg));
        }
    }

    private static NngReceiveResult copy(NngNativeReceiveResult result) {
        if (!result.isSuccess()) {
            return new NngReceiveResult(result.code(), null);
        }
        try (NativeMessage message = result.message()) {
            return new NngReceiveResult(NngErrorCode.OK, message.toByteArray());
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            int rc = nng_h.nng_socket_close(socket);
            arena.close();
            if (rc != NngErrorCode.OK && rc != NngErrorCode.ECLOSED) {
                throw new com.nz.jnng.exception.NngException(rc);
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("NNG socket is closed");
        }
    }

    private void configure() {
        Nng.check(nng_h.nng_socket_set_ms(
                socket, nng_h.NNG_OPT_SENDTIMEO(), config.sendTimeoutMillis()));
        Nng.check(nng_h.nng_socket_set_ms(
                socket, nng_h.NNG_OPT_RECVTIMEO(), config.receiveTimeoutMillis()));
        Nng.check(nng_h.nng_socket_set_ms(
                socket, nng_h.NNG_OPT_RECONNMINT(), config.reconnectMinMillis()));
        Nng.check(nng_h.nng_socket_set_ms(
                socket, nng_h.NNG_OPT_RECONNMAXT(), config.reconnectMaxMillis()));
        Nng.check(nng_h.nng_socket_set_size(
                socket, nng_h.NNG_OPT_RECVMAXSZ(), config.maxReceiveSize()));
    }

}
