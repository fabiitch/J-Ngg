package com.fabiitch.jnng.socket;

import com.fabiitch.jnng.constants.NngErrorCode;
import com.fabiitch.jnng.socket.impl.ReqSocket;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class NngNonblockingDialTest {
    @Test
    void dialReturnsWithoutWaitingForAnNngHandshake() throws Exception {
        // The TCP port exists, but its owner never speaks the NNG handshake.
        try (var endpoint = new ServerSocket(0); var socket = new ReqSocket()) {
            assertTimeout(Duration.ofSeconds(1), () -> assertEquals(NngErrorCode.OK,
                    socket.dial("tcp://127.0.0.1:" + endpoint.getLocalPort())));
        }
    }
}
