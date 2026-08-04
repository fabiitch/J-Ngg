package com.nz.jngg.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Nng {
    public void load() {
        NativeLibraryLoader.load();
    }

    public static void check(int rc) {
        if (rc != 0) {
            throw new NngException(rc);
        }
    }
}
