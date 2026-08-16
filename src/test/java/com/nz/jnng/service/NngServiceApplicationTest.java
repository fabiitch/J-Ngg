package com.nz.jnng.service;

import com.nz.jnng.Subscription;
import com.nz.jnng.codec.MessageRegistry;
import com.nz.jnng.codec.MessageType;
import com.nz.jnng.codec.PayloadCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** This test intentionally reads like the code embedded in the real executables. */
class NngServiceApplicationTest {
    private static final int DATA_TYPE_ID = 100;

    @Test
    void agentAndOverlayUseOnlyTheHighLevelTypedApi() throws Exception {
        MessageRegistry messages = MessageRegistry.builder()
                .register(new MessageType<>(DATA_TYPE_ID, Data.class, dataCodec()))
                .build();

        NngTopology<Exe> topology = NngTopology.<Exe>builder()
                .link(
                        Exe.AGENT,
                        Exe.OVERLAY,
                        "inproc://agent-overlay-" + UUID.randomUUID()
                )
                .build();

        AtomicReference<Data> overlayReceived = new AtomicReference<>();
        AtomicReference<Data> agentReceived = new AtomicReference<>();
        AtomicReference<String> rawPayload = new AtomicReference<>();
        CountDownLatch typedMessages = new CountDownLatch(2);
        CountDownLatch nativeMessage = new CountDownLatch(1);

        try (NngService<Exe> agentNngService = NngService.open(Exe.AGENT, topology, messages);
             NngService<Exe> overlayNngService = NngService.open(Exe.OVERLAY, topology, messages);
             Subscription overlayTyped = overlayNngService.on(
                     Exe.AGENT, Data.class, message -> {
                         overlayReceived.set(message);
                         typedMessages.countDown();
                     });
             Subscription overlayRaw = overlayNngService.onNativeMessage(
                     Exe.AGENT, DATA_TYPE_ID, message -> {
                         rawPayload.set(new String(
                                 message.payload().toArray(java.lang.foreign.ValueLayout.JAVA_BYTE),
                                 StandardCharsets.UTF_8));
                         nativeMessage.countDown();
                     });
             Subscription agentTyped = agentNngService.on(
                     Exe.OVERLAY, Data.class, message -> {
                         agentReceived.set(message);
                         typedMessages.countDown();
                     })) {

            agentNngService.sendTo(Exe.OVERLAY, new Data("from-agent"));
            overlayNngService.sendToAsync(Exe.AGENT, new Data("from-overlay"))
                    .get(3, TimeUnit.SECONDS);

            assertTrue(typedMessages.await(3, TimeUnit.SECONDS));
            assertTrue(nativeMessage.await(3, TimeUnit.SECONDS));
            assertEquals(new Data("from-agent"), overlayReceived.get());
            assertEquals(new Data("from-overlay"), agentReceived.get());
            assertEquals("from-agent", rawPayload.get());
        }
    }

    private static PayloadCodec<Data> dataCodec() {
        return new PayloadCodec<>() {
            @Override
            public byte[] encode(Data value) {
                return value.value().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Data decode(byte[] payload) {
                return new Data(new String(payload, StandardCharsets.UTF_8));
            }
        };
    }

    private enum Exe {
        AGENT,
        OVERLAY
    }

    private record Data(String value) {
    }
}
