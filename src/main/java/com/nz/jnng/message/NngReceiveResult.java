package com.nz.jnng.message;

import com.nz.jnng.constants.NngErrorCode;

public record NngReceiveResult(
        int code,
        byte[] data
) {

    public boolean isSuccess() {
        return code == NngErrorCode.OK;
    }
}