package com.nz.jnng.channel;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One shared Java dispatcher; NNG itself owns the native I/O threads. */
final class NngChannelExecutors {
    private static final ExecutorService SHARED = Executors.newSingleThreadExecutor(runnable ->
            Thread.ofPlatform()
                    .daemon(true)
                    .name("jnng-dispatcher")
                    .unstarted(runnable));

    private NngChannelExecutors() {
    }

    static Executor shared() {
        return SHARED;
    }
}
