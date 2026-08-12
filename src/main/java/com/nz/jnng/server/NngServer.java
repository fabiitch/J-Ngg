package com.nz.jnng.server;

import com.nz.jnng.ConnectionMode;
import com.nz.jnng.channel.*;

public final class NngServer implements INngServer {
    private final static ConnectionMode connectionMode = ConnectionMode.LISTEN;

    private final String baseAddress;
    private final NngChannelFactory channelFactory;

    public NngServer(String baseAddress, NngChannelFactory channelFactory) {
        this.baseAddress = baseAddress;
        this.channelFactory = channelFactory;
    }

    @Override
    public PairChannel pair(String channel) {
        return channelFactory.createPair(resolve(channel), connectionMode);
    }

    @Override
    public PublisherChannel pub(String channel) {
        return channelFactory.createPublisherServer(resolve(channel));
    }

    @Override
    public SubscriberChannel sub(String channel) {
        return channelFactory.createSubscriberServer(resolve(channel));
    }

    @Override
    public PushChannel push(String channel) {
        return channelFactory.createPushServer(resolve(channel));
    }

    @Override
    public PullChannel pull(String channel) {
        return channelFactory.createPullServer(resolve(channel));
    }

    @Override
    public RequestChannel req(String channel) {
        return channelFactory.createRequestServer(resolve(channel));
    }

    @Override
    public ReplyChannel rep(String channel) {
        return channelFactory.createReplyServer(resolve(channel));
    }

    private String resolve(String channel) {
        return baseAddress + "/" + channel;
    }
}