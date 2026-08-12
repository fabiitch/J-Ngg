package com.nz.jnng;

import com.nz.jnng.channel.PairChannel;
import com.nz.jnng.codec.NngMessageCodec;
import com.nz.jnng.message.NngMessage;
import com.nz.jnng.server.NngChannelFactory;
import com.nz.jnng.server.NngServer;
import org.junit.jupiter.api.Test;

public class ServerTest {

    @Test
    public void testServer() {

        NngMessageCodec nngMessageCodec = new NngMessageCodec() {
            @Override
            public byte[] encode(NngMessage message) {
                return new byte[0];
            }

            @Override
            public NngMessage decode(byte[] data) {
                return null;
            }
        };

        NngServer server = new NngServer("test", new NngChannelFactory(nngMessageCodec));
        PairChannel channel1Pair = server.pair("channel1Pair");

        channel1Pair.send();
    }
}
