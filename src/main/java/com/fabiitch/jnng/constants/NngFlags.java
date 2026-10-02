package com.fabiitch.jnng.constants;

import com.fabiitch.jnng.nng_h;

public final class NngFlags {

    private NngFlags() {
    }

    public static final int NONE = 0;
    public static final int NONBLOCK = nng_h.NNG_FLAG_NONBLOCK();
}
