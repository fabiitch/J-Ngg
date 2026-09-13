package com.fabiitch.jnng.service.channel;

import com.fabiitch.jnng.socket.NativeMessage;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WireProtocolTest {

    @Test
    void rejectsUnsupportedVersionFromJavaAndNativeMemory() {
        byte[] frame = ByteBuffer.allocate(WireProtocol.HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(WireProtocol.VERSION + 1)
                .putInt(1)
                .putLong(1)
                .putLong(0)
                .putInt(0)
                .array();

        assertThrows(IllegalArgumentException.class, () -> WireProtocol.decode(frame));
        try (NativeMessage message = NativeMessage.copyOf(frame)) {
            assertThrows(IllegalArgumentException.class, () -> WireProtocol.decode(message));
        }
    }
}
