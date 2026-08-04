package com.nz.jngg;

import com.nz.jngg.impl.Pair1Socket;
import com.nz.jngg.utils.Nng;
import com.nz.jnng.nng_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One bidirectional 1-to-1 channel. Both endpoints may initiate requests.
 * The channel creates no Java thread.
 */
public final class NggPeerChannel implements AutoCloseable {
    private final NngSocketConfig config;
    private final Pair1Socket socket;
    private final Semaphore pendingPermits;
    private final Set<Long> pendingRequestIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong requestSequence = new AtomicLong();
    private final AtomicReference<Role> role = new AtomicReference<>();

    public NggPeerChannel() {
        this(NngSocketConfig.defaults());
    }

    public NggPeerChannel(NngSocketConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        Nng.load();
        socket = new Pair1Socket();
        pendingPermits = new Semaphore(config.maxPendingRequestsPerPeer(), true);
        configureSocket();
    }

    /** Makes this endpoint the accepting side of this single peer channel. */
    public void listen(String address) {
        selectRole(Role.LISTENER);
        socket.listen(address);
    }

    /** Starts connecting asynchronously; NNG retries at the configured fixed interval. */
    public void connect(String address) {
        selectRole(Role.DIALER);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeAddress = arena.allocateFrom(address);
            Nng.check(nng_h.nng_dial(socket.getSocket(), nativeAddress,
                    MemorySegment.NULL, nng_h.NNG_FLAG_NONBLOCK()));
        }
    }

    /** Sends a request and returns the generated channel-unique request id. */
    public long sendRequest(short messageType, byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        if (!pendingPermits.tryAcquire()) {
            throw new TooManyPendingRequestsException(config.maxPendingRequestsPerPeer());
        }

        long requestId = nextRequestId();
        pendingRequestIds.add(requestId);
        try {
            socket.send(NggWireCodec.encode(messageType, requestId, payload));
            return requestId;
        } catch (RuntimeException exception) {
            pendingRequestIds.remove(requestId);
            pendingPermits.release();
            throw exception;
        }
    }

    /** Receives either a new peer request or a response to one of our requests. */
    public NggMessage receive() {
        return NggWireCodec.decode(socket.receive());
    }

    /** Replies to a received request while preserving its request id. */
    public void reply(NggMessage request, short messageType, byte[] payload) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(payload, "payload");
        socket.send(NggWireCodec.encode(messageType, request.header().requestId(), payload));
    }

    /**
     * Marks a received message as the response to one of our requests and frees
     * one pending-request slot.
     */
    public NggMessage completeResponse(long requestId, NggMessage response) {
        Objects.requireNonNull(response, "response");
        if (response.header().requestId() != requestId) {
            throw new IllegalArgumentException("Unexpected response requestId: expected="
                    + requestId + ", actual=" + response.header().requestId());
        }
        if (!pendingRequestIds.remove(requestId)) {
            throw new IllegalStateException("Request is not pending: " + requestId);
        }
        pendingPermits.release();
        return response;
    }

    public boolean isResponse(NggMessage message) {
        Objects.requireNonNull(message, "message");
        return pendingRequestIds.contains(message.header().requestId());
    }

    @Override
    public void close() {
        socket.close();
    }

    private void configureSocket() {
        int timeout = config.requestTimeoutMillis();
        Nng.check(nng_h.nng_socket_set_ms(socket.getSocket(), nng_h.NNG_OPT_SENDTIMEO(), timeout));
        Nng.check(nng_h.nng_socket_set_ms(socket.getSocket(), nng_h.NNG_OPT_RECVTIMEO(), timeout));
        Nng.check(nng_h.nng_socket_set_ms(socket.getSocket(), nng_h.NNG_OPT_RECONNMINT(),
                config.reconnectIntervalMillis()));
        Nng.check(nng_h.nng_socket_set_ms(socket.getSocket(), nng_h.NNG_OPT_RECONNMAXT(), 0));
    }

    private void selectRole(Role selectedRole) {
        Role current = role.get();
        if (current == selectedRole) {
            throw new IllegalStateException("Channel endpoint is already started as " + current);
        }
        if (current != null || !role.compareAndSet(null, selectedRole)) {
            throw new IllegalStateException("A channel endpoint can only listen or connect once");
        }
    }

    private long nextRequestId() {
        Role currentRole = role.get();
        if (currentRole == null) {
            throw new IllegalStateException("Call listen() or connect() before sending a request");
        }
        long sequence = requestSequence.updateAndGet(current ->
                current >= (Long.MAX_VALUE >>> 1) ? 1 : current + 1);
        return (sequence << 1) | currentRole.requestIdBit;
    }

    private enum Role {
        LISTENER(0),
        DIALER(1);

        private final int requestIdBit;

        Role(int requestIdBit) {
            this.requestIdBit = requestIdBit;
        }
    }
}
