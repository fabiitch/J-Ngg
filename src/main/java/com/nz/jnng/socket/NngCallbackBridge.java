package com.nz.jnng.socket;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps Java/application work outside native NNG callback stacks. */
public final class NngCallbackBridge {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable ->
            Thread.ofPlatform().daemon(true).name("jnng-native-events").unstarted(runnable));

    private NngCallbackBridge() {
    }

    public static void execute(Runnable task) {
        EXECUTOR.execute(task);
    }
}
