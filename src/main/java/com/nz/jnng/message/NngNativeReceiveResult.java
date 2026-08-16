package com.nz.jnng.message;

import com.nz.jnng.constants.NngErrorCode;
import com.nz.jnng.socket.NativeMessage;

public record NngNativeReceiveResult(int code, NativeMessage message) {
    public boolean isSuccess() {
        return code == NngErrorCode.OK;
    }
}
