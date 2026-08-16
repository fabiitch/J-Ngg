package com.nz.jnng.codec;

import com.nz.jnng.message.WireMessage;
import com.nz.jnng.message.NativeWireMessage;
import com.nz.jnng.socket.NativeMessage;

/** Encodes and decodes the stable J-NNG transport envelope. */
public interface WireCodec {

    byte[] encode(WireMessage message);

    NativeMessage encodeNative(WireMessage message);

    WireMessage decode(byte[] data);

    NativeWireMessage decodeNative(NativeMessage message);
}
