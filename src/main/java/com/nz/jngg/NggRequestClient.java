package com.nz.jngg;

import com.nz.jngg.exception.NggRequestTimeoutException;
import com.nz.jngg.exception.TooManyPendingRequestsException;
import com.nz.jngg.impl.ReqSocket;
import com.nz.jngg.message.NggMessage;
import com.nz.jngg.utils.Nng;
import com.nz.jnng.nng_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A synchronous, bounded request client for one remote peer.
 * It owns no Java thread; callers execute the request on their own thread.
 */
public final class NggRequestClient implements AutoCloseable {
    private final NngSocketConfig config;
    private final ReqSocket socket;
    private final Semaphore pendingRequests;
    private final ReentrantLock transactionLock = new ReentrantLock(true);
    private final AtomicLong requestIds = new AtomicLong();

    public NggRequestClient() {
        this(NngSocketConfig.defaults());
    }

    public NggRequestClient(NngSocketConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        Nng.load();
        this.socket = new ReqSocket();
        this.pendingRequests = new Semaphore(config.maxPendingRequestsPerPeer(), true);
        configureSocket(config.requestTimeoutMillis());
    }

    /** Starts connecting and returns immediately. NNG retries in the background. */
    public void connect(String address) {
        Objects.requireNonNull(address, "address");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeAddress = arena.allocateFrom(address);
            Nng.check(nng_h.nng_dial(socket.getSocket(), nativeAddress,
                    MemorySegment.NULL, nng_h.NNG_FLAG_NONBLOCK()));
        }
    }

    public NggMessage request(short messageType, byte[] payload) throws InterruptedException {
        Objects.requireNonNull(payload, "payload");
        if (!pendingRequests.tryAcquire()) {
            throw new TooManyPendingRequestsException(config.maxPendingRequestsPerPeer());
        }

        long deadline = deadlineNanos();
        boolean locked = false;
        try {
            locked = acquireTransaction(deadline);
            if (!locked) {
                throw new NggRequestTimeoutException();
            }

            long requestId = nextRequestId();
            configureOperationTimeout(deadline);
            socket.send(NggWireCodec.encode(messageType, requestId, payload));

            configureOperationTimeout(deadline);
            NggMessage response = NggWireCodec.decode(socket.receive());
            if (response.header().requestId() != requestId) {
                throw new IllegalStateException("Response requestId does not match request: expected="
                        + requestId + ", actual=" + response.header().requestId());
            }
            return response;
        } finally {
            if (locked) {
                transactionLock.unlock();
            }
            pendingRequests.release();
        }
    }

    @Override
    public void close() {
        socket.close();
    }

    private void configureSocket(int timeoutMillis) {
        Nng.check(nng_h.nng_socket_set_ms(
                socket.getSocket(), nng_h.NNG_OPT_SENDTIMEO(), timeoutMillis));
        Nng.check(nng_h.nng_socket_set_ms(
                socket.getSocket(), nng_h.NNG_OPT_RECVTIMEO(), timeoutMillis));
        Nng.check(nng_h.nng_socket_set_ms(socket.getSocket(), nng_h.NNG_OPT_RECONNMINT(),
                config.reconnectIntervalMillis()));
        // Zero disables exponential backoff: retries always use RECONNMINT.
        Nng.check(nng_h.nng_socket_set_ms(
                socket.getSocket(), nng_h.NNG_OPT_RECONNMAXT(), 0));
    }

    private long deadlineNanos() {
        return config.requestTimeout()
                .map(timeout -> {
                    long now = System.nanoTime();
                    long duration = timeout.toNanos();
                    long deadline = now + duration;
                    return deadline < 0 ? Long.MAX_VALUE : deadline;
                })
                .orElse(Long.MAX_VALUE);
    }

    private boolean acquireTransaction(long deadline) throws InterruptedException {
        if (deadline == Long.MAX_VALUE) {
            transactionLock.lockInterruptibly();
            return true;
        }
        long remaining = deadline - System.nanoTime();
        return remaining > 0 && transactionLock.tryLock(remaining, TimeUnit.NANOSECONDS);
    }

    private void configureOperationTimeout(long deadline) {
        int timeoutMillis;
        if (deadline == Long.MAX_VALUE) {
            timeoutMillis = nng_h.NNG_DURATION_INFINITE();
        } else {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new NggRequestTimeoutException();
            }
            long roundedUpMillis = Math.max(1,
                    TimeUnit.NANOSECONDS.toMillis(remainingNanos) + 1);
            timeoutMillis = (int) Math.min(Integer.MAX_VALUE, roundedUpMillis);
        }
        Nng.check(nng_h.nng_socket_set_ms(
                socket.getSocket(), nng_h.NNG_OPT_SENDTIMEO(), timeoutMillis));
        Nng.check(nng_h.nng_socket_set_ms(
                socket.getSocket(), nng_h.NNG_OPT_RECVTIMEO(), timeoutMillis));
    }

    private long nextRequestId() {
        return requestIds.updateAndGet(current -> current == Long.MAX_VALUE ? 1 : current + 1);
    }
}
