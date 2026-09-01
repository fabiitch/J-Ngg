package com.nz.jnng.utils;

import com.nz.jnng.constants.NngErrorCode;
import com.nz.jnng.exception.NngException;
import com.nz.jnng.nng_h;
import com.nz.jnng.socket.NngRuntimeConfig;
import lombok.experimental.UtilityClass;

import java.util.Objects;

@UtilityClass
public class Nng {
    private NngRuntimeConfig runtimeConfig;

    public synchronized void load() {
        if (runtimeConfig == null) {
            initialize(NngRuntimeConfig.defaults());
        }
    }

    public synchronized void initialize(NngRuntimeConfig requestedConfig) {
        Objects.requireNonNull(requestedConfig, "requestedConfig");
        if (runtimeConfig != null) {
            if (!runtimeConfig.equals(requestedConfig)) {
                throw new IllegalStateException("NNG is already initialized with "
                        + runtimeConfig + "; requested " + requestedConfig);
            }
            return;
        }

        NativeLibraryLoader.load();
        nng_h.nng_init_set_parameter(
                nng_h.NNG_INIT_NUM_TASK_THREADS(), requestedConfig.taskThreads());
        nng_h.nng_init_set_parameter(
                nng_h.NNG_INIT_NUM_EXPIRE_THREADS(), requestedConfig.expireThreads());
        nng_h.nng_init_set_parameter(
                nng_h.NNG_INIT_NUM_POLLER_THREADS(), requestedConfig.pollerThreads());
        nng_h.nng_init_set_parameter(
                nng_h.NNG_INIT_NUM_RESOLVER_THREADS(), requestedConfig.resolverThreads());
        runtimeConfig = requestedConfig;
    }

    public synchronized NngRuntimeConfig runtimeConfig() {
        return runtimeConfig;
    }

    public static boolean isOk(int rc) {
        return rc == NngErrorCode.OK;
    }

    public static void check(int rc) {
        if (rc != NngErrorCode.OK) {
            throw new NngException(rc);
        }
    }
}
