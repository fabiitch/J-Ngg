package com.nz.jnng.constants;

/**
 * NNG error codes.
 *
 * <p>Values are mapped directly from the native {@code nng_err} enum.</p>
 */
public final class NngErrorCode {

    private NngErrorCode() {
    }

    public static final int OK = 0;

    public static final int EINTR = 1;
    public static final int ENOMEM = 2;
    public static final int EINVAL = 3;
    public static final int EBUSY = 4;
    public static final int ETIMEDOUT = 5;
    public static final int ECONNREFUSED = 6;
    public static final int ECLOSED = 7;
    public static final int EAGAIN = 8;
    public static final int ENOTSUP = 9;
    public static final int EADDRINUSE = 10;
    public static final int ESTATE = 11;
    public static final int ENOENT = 12;
    public static final int EPROTO = 13;
    public static final int EUNREACHABLE = 14;
    public static final int EADDRINVAL = 15;
    public static final int EPERM = 16;
    public static final int EMSGSIZE = 17;
    public static final int ECONNABORTED = 18;
    public static final int ECONNRESET = 19;
    public static final int ECANCELED = 20;
    public static final int ENOFILES = 21;
    public static final int ENOSPC = 22;
    public static final int EEXIST = 23;
    public static final int EREADONLY = 24;
    public static final int EWRITEONLY = 25;
    public static final int ECRYPTO = 26;
    public static final int EPEERAUTH = 27;

    public static final int EBADTYPE = 30;
    public static final int ECONNSHUT = 31;

    public static final int ESTOPPED = 999;
    public static final int EINTERNAL = 1000;

    /**
     * Flag indicating an operating-system error.
     */
    public static final int ESYSERR = 0x10000000;

    /**
     * Flag indicating a transport-specific error.
     */
    public static final int ETRANERR = 0x20000000;
}