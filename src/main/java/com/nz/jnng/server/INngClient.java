package com.nz.jnng.server;

import com.nz.jnng.channel.*;

public interface INngClient extends AutoCloseable {
    PairChannel pair(String channel);
    PublisherChannel pub(String channel);
    SubscriberChannel sub(String channel);
    PushChannel push(String channel);
    PullChannel pull(String channel);
    RequestChannel req(String channel);
    ReplyChannel rep(String channel);
    @Override void close();
}
