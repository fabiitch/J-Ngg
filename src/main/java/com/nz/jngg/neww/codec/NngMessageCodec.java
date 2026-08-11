package com.nz.jngg.neww.codec;

import com.nz.jngg.neww.message.NngMessage;

public interface NngMessageCodec {
    byte[] encode(NngMessage message);

    NngMessage decode(byte[] data);
}
