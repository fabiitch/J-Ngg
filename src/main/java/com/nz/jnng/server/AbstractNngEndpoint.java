package com.nz.jnng.server;

import com.nz.jnng.ConnectionMode;
import com.nz.jnng.channel.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

abstract class AbstractNngEndpoint implements AutoCloseable {
    private final String baseAddress;
    private final NngChannelFactory factory;
    private final ConnectionMode mode;
    private final List<AutoCloseable> channels =
            Collections.synchronizedList(new ArrayList<>());

    AbstractNngEndpoint(
            String baseAddress,
            NngChannelFactory factory,
            ConnectionMode mode
    ) {
        this.baseAddress = Objects.requireNonNull(baseAddress, "baseAddress");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public PairChannel pair(String name) { return track(factory.createPair(resolve(name), mode)); }
    public PublisherChannel pub(String name) {
        return track(factory.createPublisher(resolve(name), mode));
    }
    public SubscriberChannel sub(String name) {
        return track(factory.createSubscriber(resolve(name), mode));
    }
    public PushChannel push(String name) { return track(factory.createPush(resolve(name), mode)); }
    public PullChannel pull(String name) { return track(factory.createPull(resolve(name), mode)); }
    public RequestChannel req(String name) {
        return track(factory.createRequest(resolve(name), mode));
    }
    public ReplyChannel rep(String name) { return track(factory.createReply(resolve(name), mode)); }

    private String resolve(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("channel name must not be blank");
        return baseAddress.endsWith("/") ? baseAddress + name : baseAddress + "/" + name;
    }

    private <T extends AutoCloseable> T track(T channel) {
        channels.add(channel);
        return channel;
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        synchronized (channels) {
            for (int index = channels.size() - 1; index >= 0; index--) {
                try {
                    channels.get(index).close();
                } catch (Exception error) {
                    if (failure == null) failure = new RuntimeException("Failed to close endpoint");
                    failure.addSuppressed(error);
                }
            }
            channels.clear();
        }
        if (failure != null) throw failure;
    }
}
