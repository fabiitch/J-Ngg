package com.nz.jnng.socket;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NngSocketConfigTest {
    @Test
    void exposesFiniteOperationalDefaultsAndInfiniteReceive() {
        NngSocketConfig config = NngSocketConfig.defaults();

        assertTrue(config.sendTimeout().isPresent());
        assertTrue(config.receiveTimeout().isEmpty());
        assertTrue(config.requestTimeout().isPresent());
        assertEquals(16L * 1024 * 1024, config.maxReceiveSize());
    }

    @Test
    void validatesReconnectBoundsAndDurations() {
        assertThrows(IllegalArgumentException.class, () ->
                NngSocketConfig.defaults().withReconnect(
                        Duration.ofSeconds(2), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () ->
                NngSocketConfig.defaults().withSendTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                NngSocketConfig.defaults().withMaxReceiveSize(0));
    }
}
