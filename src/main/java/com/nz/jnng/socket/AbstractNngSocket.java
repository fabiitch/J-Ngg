package com.nz.jnng.socket;

import com.nz.jnng.Subscription;
import com.nz.jnng.constants.NngFlags;
import com.nz.jnng.nng_pipe_cb;
import com.nz.jnng.nng_h;
import com.nz.jnng.nng_socket;
import com.nz.jnng.utils.Nng;
import com.nz.jnng.constants.NngErrorCode;
import lombok.Getter;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class AbstractNngSocket implements INngSocket {

    protected final Arena arena;
    @Getter
    protected final MemorySegment socket;
    private final NngSocketConfig config;
    private final NngAio.Receiver receiver;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final CopyOnWriteArrayList<Consumer<SocketConnectionEvent>> connectionListeners =
            new CopyOnWriteArrayList<>();

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
        NngAio.Receiver initializedReceiver;
        try {
            configure();
            registerPipeNotifications();
            initializedReceiver = NngAio.receiver(socket);
        } catch (Throwable error) {
            nng_h.nng_socket_close(socket);
            arena.close();
            throw error;
        }
        this.receiver = initializedReceiver;
    }

    protected abstract int open(MemorySegment socket);

    @Override
    public int listen(String address) {
        ensureOpen();
        Objects.requireNonNull(address, "address");
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
        ensureOpen();
        Objects.requireNonNull(address, "address");
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
    public CompletableFuture<Void> sendAsync(byte[] payload) {
        ensureOpen();
        return NngAio.send(socket, payload, config.sendTimeout());
    }

    @Override
    public NngSocketConfig config() {
        return config;
    }

    private int send(byte[] payload, int flags) {
        ensureOpen();
        Objects.requireNonNull(payload, "payload");
        NativeMessage message = NativeMessage.copyOf(payload);
        int rc = nng_h.nng_sendmsg(socket, message.handle(), flags);
        if (rc == NngErrorCode.OK) {
            message.transferredToNng();
        } else {
            message.close();
        }
        return rc;
    }

    @Override
    public CompletableFuture<NativeMessage> receiveNativeAsync() {
        ensureOpen();
        return receiver.receive(config.receiveTimeout());
    }

    @Override
    public CompletableFuture<NativeMessage> receiveNativeAsync(Duration timeout) {
        ensureOpen();
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be > 0");
        }
        return receiver.receive(Optional.of(timeout));
    }

    @Override
    public Subscription onConnectionChanged(Consumer<SocketConnectionEvent> listener) {
        ensureOpen();
        Objects.requireNonNull(listener, "listener");
        connectionListeners.add(listener);
        return new Subscription() {
            private final AtomicBoolean active = new AtomicBoolean(true);

            @Override
            public void close() {
                if (active.compareAndSet(true, false)) {
                    connectionListeners.remove(listener);
                }
            }

            @Override
            public boolean isActive() {
                return active.get() && !closed.get();
            }
        };
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            connectionListeners.clear();
            receiver.close();
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

    private void registerPipeNotifications() {
        MemorySegment callback = nng_pipe_cb.allocate(this::handlePipeEvent, arena);
        Nng.check(nng_h.nng_pipe_notify(
                socket,
                nng_h.NNG_PIPE_EV_ADD_POST(),
                callback,
                MemorySegment.NULL
        ));
        Nng.check(nng_h.nng_pipe_notify(
                socket,
                nng_h.NNG_PIPE_EV_REM_POST(),
                callback,
                MemorySegment.NULL
        ));
    }

    /** Called under an NNG socket lock: listeners must only enqueue lightweight work. */
    private void handlePipeEvent(MemorySegment ignoredPipe, int event, MemorySegment ignoredArg) {
        try {
            int count;
            if (event == nng_h.NNG_PIPE_EV_ADD_POST()) {
                count = activeConnections.incrementAndGet();
            } else if (event == nng_h.NNG_PIPE_EV_REM_POST()) {
                count = activeConnections.updateAndGet(current -> Math.max(0, current - 1));
            } else {
                return;
            }
            SocketConnectionEvent connectionEvent = new SocketConnectionEvent(count);
            NngCallbackBridge.execute(() -> {
                for (Consumer<SocketConnectionEvent> listener : connectionListeners) {
                    try {
                        listener.accept(connectionEvent);
                    } catch (Throwable ignored) {
                        // A low-level listener cannot stop pipe event delivery.
                    }
                }
            });
        } catch (Throwable ignored) {
            // Exceptions must never cross the native upcall boundary.
        }
    }

}
