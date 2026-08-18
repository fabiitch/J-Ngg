package com.nz.jnng.neww;

import com.nz.jnng.neww.communication.PairChannel;
import com.nz.jnng.neww.communication.PubChannel;
import com.nz.jnng.neww.communication.PullChannel;
import com.nz.jnng.neww.communication.PushChannel;
import com.nz.jnng.neww.communication.RepChannel;
import com.nz.jnng.neww.communication.ReqChannel;
import com.nz.jnng.neww.communication.SubChannel;
import com.nz.jnng.socket.NngCallbackBridge;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/** Owner and factory for all application channels in one process. */
public final class Jnng implements AutoCloseable {
    private final Executor dispatcherExecutor;
    private final ExecutorService ownedExecutor;
    private final List<AbstractChannel> channels = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    /** Creates a JNNG instance with one shared daemon dispatcher thread. */
    public Jnng() {
        ownedExecutor = Executors.newSingleThreadExecutor(runnable ->
                Thread.ofPlatform().daemon(true).name("jnng-dispatcher").unstarted(runnable));
        dispatcherExecutor = ownedExecutor;
    }

    /** Uses an application-owned executor for every channel callback. */
    public Jnng(Executor executor) {
        dispatcherExecutor = Objects.requireNonNull(executor, "executor");
        ownedExecutor = null;
    }

    public PairChannel pair(ChannelConfiguration configuration) {
        return create(configuration, PairChannel::new);
    }

    public PubChannel pub(ChannelConfiguration configuration) {
        return create(configuration, PubChannel::new);
    }

    public SubChannel sub(ChannelConfiguration configuration) {
        return create(configuration, SubChannel::new);
    }

    public PushChannel push(ChannelConfiguration configuration) {
        return create(configuration, PushChannel::new);
    }

    public PullChannel pull(ChannelConfiguration configuration) {
        return create(configuration, PullChannel::new);
    }

    public ReqChannel req(ChannelConfiguration configuration) {
        return create(configuration, ReqChannel::new);
    }

    public RepChannel rep(ChannelConfiguration configuration) {
        return create(configuration, RepChannel::new);
    }

    private <C extends AbstractChannel> C create(
            ChannelConfiguration configuration,
            BiFunction<ChannelConfiguration, Executor, C> factory
    ) {
        if (closed.get()) throw new IllegalStateException("Jnng is closed");
        C channel = factory.apply(Objects.requireNonNull(configuration, "configuration"),
                dispatcherExecutor);
        channels.add(channel);
        return channel;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        RuntimeException failure = null;
        for (int index = channels.size() - 1; index >= 0; index--) {
            try {
                channels.get(index).close();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        channels.clear();
        if (ownedExecutor != null) NngCallbackBridge.execute(ownedExecutor::shutdown);
        if (failure != null) throw failure;
    }
}
