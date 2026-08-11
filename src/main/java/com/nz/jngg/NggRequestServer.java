package com.nz.jngg;

import com.nz.jngg.impl.RepSocket;
import com.nz.jngg.message.NggMessage;
import com.nz.jngg.utils.Nng;
import com.nz.jnng.nng_h;

import java.util.Objects;

/**
 * A sequential request/reply server accepting any number of connected clients.
 * The application chooses how many handler threads call it; the class creates none.
 */
public final class NggRequestServer implements AutoCloseable {
    private final RepSocket socket;

    public NggRequestServer() {
        this(NngSocketConfig.defaults());
    }

    public NggRequestServer(NngSocketConfig config) {
        Objects.requireNonNull(config, "config");
        Nng.load();
        socket = new RepSocket();
        Nng.check(nng_h.nng_socket_set_ms(socket.getSocket(), nng_h.NNG_OPT_RECVTIMEO(),
                nng_h.NNG_DURATION_INFINITE()));
        Nng.check(nng_h.nng_socket_set_ms(socket.getSocket(), nng_h.NNG_OPT_SENDTIMEO(),
                config.requestTimeoutMillis()));
    }

    public void listen(String address) {
        socket.listen(address);
    }

    public NggMessage receive() {
        return NggWireCodec.decode(socket.receive());
    }

    public void reply(NggMessage request, short messageType, byte[] payload) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(payload, "payload");
        socket.send(NggWireCodec.encode(messageType, request.header().requestId(), payload));
    }

    @Override
    public void close() {
        socket.close();
    }
}
