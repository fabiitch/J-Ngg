package com.nz.jngg.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Nng {
    public void load() {
        NativeLibraryLoader.load();
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
