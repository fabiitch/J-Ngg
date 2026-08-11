package com.nz.jngg.neww.server;

import com.nz.jngg.impl.Pair1Socket;
import com.nz.jngg.neww.ConnectionMode;
import com.nz.jngg.neww.channel.*;
import com.nz.jngg.neww.codec.NngMessageCodec;
import com.nz.jngg.utils.NngErrorCode;
import com.nz.jngg.utils.NngException;

public final class NngChannelFactory {
    private final NngMessageCodec codec;

    public NngChannelFactory(NngMessageCodec codec) {
        this.codec = codec;
    }

    public PairChannel createPair(String address, ConnectionMode connectionMode) {
        Pair1Socket socket = new Pair1Socket();

        int rc = switch (connectionMode) {
            case LISTEN -> socket.listen(address);
            case DIAL -> socket.dial(address);
        };

        if (rc != NngErrorCode.OK) {
            socket.close();
            throw new NngException(rc);
        }

        return new PairChannel(socket, codec);
    }

    PublisherChannel createPublisherServer(String address) {
        return null;
    }

    PullChannel createPullServer(String address) {
        return null;
    }

    public SubscriberChannel createSubscriberServer(String resolve) {
    }

    public PushChannel createPushServer(String resolve) {
    }

    public RequestChannel createRequestServer(String resolve) {
    }

    public ReplyChannel createReplyServer(String resolve) {
    }

}
