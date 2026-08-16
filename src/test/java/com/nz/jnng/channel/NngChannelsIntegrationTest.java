package com.nz.jnng.channel;

import com.nz.jnng.Subscription;
import com.nz.jnng.codec.BinaryWireCodec;
import com.nz.jnng.message.WireMessage;
import com.nz.jnng.server.NngChannelFactory;
import com.nz.jnng.server.NngClient;
import com.nz.jnng.server.NngServer;
import com.nz.jnng.socket.NngSocketConfig;
import com.nz.jnng.exception.NggRequestTimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NngChannelsIntegrationTest {
    private final NngChannelFactory factory = new NngChannelFactory(new BinaryWireCodec());

    @Test
    void serverAndClientExposeEveryNngPattern() throws Exception {
        String base = "inproc://channels-" + UUID.randomUUID();
        try (NngServer server = new NngServer(base, factory);
             NngClient client = new NngClient(base, factory)) {
            PairChannel serverPair = server.pair("pair");
            PublisherChannel publisher = server.pub("pubsub");
            PullChannel pull = server.pull("pipeline");
            ReplyChannel reply = server.rep("request");

            PairChannel clientPair = client.pair("pair");
            SubscriberChannel subscriber = client.sub("pubsub");
            PushChannel push = client.push("pipeline");
            RequestChannel request = client.req("request");

            clientPair.sendAsync(message(1, "pair-client")).get(3, TimeUnit.SECONDS);
            assertWire(message(1, "pair-client"),
                    serverPair.receive(Duration.ofSeconds(3)).orElseThrow());
            serverPair.send(message(2, "pair-server"));
            assertWire(message(2, "pair-server"),
                    clientPair.receive(Duration.ofSeconds(3)).orElseThrow());

            push.push(message(3, "work"));
            assertWire(message(3, "work"), pull.pull(Duration.ofSeconds(3)).orElseThrow());

            CountDownLatch replied = new CountDownLatch(1);
            try (Subscription ignored = reply.onRequest(incoming -> {
                replied.countDown();
                return new WireMessage(1, 5, 200, incoming.messageId(), bytes("pong"));
            })) {
                WireMessage response = request.requestAsync(message(4, "ping"))
                        .get(3, TimeUnit.SECONDS);
                assertEquals(5, response.messageTypeId());
                assertEquals("pong", text(response.payload()));
                assertTrue(replied.await(3, TimeUnit.SECONDS));
            }

            // PUB/SUB subscriptions need a brief propagation window.
            Thread.sleep(100);
            publisher.publish(message(6, "broadcast"));
            assertWire(message(6, "broadcast"),
                    subscriber.receive(Duration.ofSeconds(3)).orElseThrow());
        }
    }

    @Test
    void requestUsesTheConfiguredDefaultTimeout() {
        String base = "inproc://request-timeout-" + UUID.randomUUID();
        NngSocketConfig config = NngSocketConfig.defaults()
                .withRequestTimeout(Duration.ofMillis(50));
        NngChannelFactory configuredFactory =
                new NngChannelFactory(new BinaryWireCodec(), config);

        try (NngServer server = new NngServer(base, configuredFactory);
             NngClient client = new NngClient(base, configuredFactory)) {
            server.rep("request");
            RequestChannel request = client.req("request");

            assertThrows(NggRequestTimeoutException.class,
                    () -> request.request(message(7, "no-reply")));

            ExecutionException asyncTimeout = assertThrows(ExecutionException.class,
                    () -> request.requestAsync(message(8, "still-no-reply"))
                            .get(3, TimeUnit.SECONDS));
            assertTrue(asyncTimeout.getCause() instanceof NggRequestTimeoutException);
        }
    }

    private static WireMessage message(int type, String payload) {
        return new WireMessage(1, type, type * 10L, 0, bytes(payload));
    }

    private static void assertWire(WireMessage expected, WireMessage actual) {
        assertEquals(expected.wireVersion(), actual.wireVersion());
        assertEquals(expected.messageTypeId(), actual.messageTypeId());
        assertEquals(expected.messageId(), actual.messageId());
        assertEquals(expected.correlationId(), actual.correlationId());
        assertArrayEquals(expected.payload(), actual.payload());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
