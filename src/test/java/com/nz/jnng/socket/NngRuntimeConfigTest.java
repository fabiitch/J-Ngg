package com.nz.jnng.socket;

import com.nz.jnng.service.Jnng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NngRuntimeConfigTest {

    @Test
    void usesLowResourceDefaultsAndSharesThemBetweenJnngInstances() {
        NngRuntimeConfig defaults = NngRuntimeConfig.defaults();
        assertEquals(new NngRuntimeConfig(2, 1, 1, 1), defaults);

        try (Jnng first = new Jnng(defaults); Jnng second = new Jnng(defaults)) {
            assertEquals(defaults, first.runtimeConfig());
            assertEquals(defaults, second.runtimeConfig());
        }
    }

    @Test
    void rejectsInvalidOrConflictingProcessConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new NngRuntimeConfig(1, 1, 1, 1));

        NngRuntimeConfig defaults = NngRuntimeConfig.defaults();
        try (Jnng ignored = new Jnng(defaults)) {
            NngRuntimeConfig conflicting = NngRuntimeConfig.builder()
                    .taskThreads(3)
                    .build();
            assertThrows(IllegalStateException.class, () -> new Jnng(conflicting));
        }
    }
}
