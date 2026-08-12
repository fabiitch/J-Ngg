package com.nz.jnng.exception;

import com.nz.jnng.nng_h;
import lombok.Getter;

@Getter
public final class NngException extends RuntimeException {

    private final int code;

    public NngException(int code) {
        System.out.println("NNG ERROR = " + code);
        super(nng_h.nng_strerror(code).getString(0));
        this.code = code;
    }
}
