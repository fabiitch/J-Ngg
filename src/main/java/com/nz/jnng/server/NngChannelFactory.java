package com.nz.jnng.server;

import com.nz.jnng.ConnectionMode;
import com.nz.jnng.channel.*;
import com.nz.jnng.codec.WireCodec;
import com.nz.jnng.constants.NngErrorCode;
import com.nz.jnng.exception.NngException;
import com.nz.jnng.socket.INngSocket;
import com.nz.jnng.socket.impl.*;
import com.nz.jnng.socket.NngSocketConfig;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class NngChannelFactory {
    private final WireCodec codec;
    private final NngSocketConfig socketConfig;

    public NngChannelFactory(WireCodec codec) {
        this(codec, NngSocketConfig.defaults());
    }

    public NngChannelFactory(WireCodec codec, NngSocketConfig socketConfig) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.socketConfig = Objects.requireNonNull(socketConfig, "socketConfig");
    }

    public PairChannel createPair(String address, ConnectionMode mode) {
        return create(() -> new Pair1Socket(socketConfig), PairChannel::new, address, mode, false);
    }

    public PublisherChannel createPublisher(String address, ConnectionMode mode) {
        return create(() -> new PubSocket(socketConfig), PublisherChannel::new, address, mode, false);
    }

    public SubscriberChannel createSubscriber(String address, ConnectionMode mode) {
        return create(() -> new SubSocket(socketConfig), SubscriberChannel::new, address, mode, true);
    }

    public PushChannel createPush(String address, ConnectionMode mode) {
        return create(() -> new PushSocket(socketConfig), PushChannel::new, address, mode, false);
    }

    public PullChannel createPull(String address, ConnectionMode mode) {
        return create(() -> new PullSocket(socketConfig), PullChannel::new, address, mode, false);
    }

    public RequestChannel createRequest(String address, ConnectionMode mode) {
        return create(() -> new ReqSocket(socketConfig), RequestChannel::new, address, mode, false);
    }

    public ReplyChannel createReply(String address, ConnectionMode mode) {
        return create(() -> new RepSocket(socketConfig), ReplyChannel::new, address, mode, false);
    }

    private <S extends INngSocket, C> C create(
            Supplier<S> socketSupplier,
            BiFunction<INngSocket, WireCodec, C> channelFactory,
            String address,
            ConnectionMode mode,
            boolean subscribeAll
    ) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(mode, "mode");
        S socket = socketSupplier.get();
        try {
            if (subscribeAll) ((SubSocket) socket).subscribe(new byte[0]);
            int rc = mode == ConnectionMode.LISTEN
                    ? socket.listen(address)
                    : socket.dial(address);
            if (rc != NngErrorCode.OK) throw new NngException(rc);
            return channelFactory.apply(socket, codec);
        } catch (Throwable error) {
            socket.close();
            throw error;
        }
    }
}
