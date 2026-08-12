package com.nz.jnng.codec;

import com.nz.jnng.message.NngMessage;

public interface NngMessageCodec {
    byte[] encode(NngMessage message);

    NngMessage decode(byte[] data);
}
