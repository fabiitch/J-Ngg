package com.nz.jnng.service;

import com.nz.jnng.Subscription;
import com.nz.jnng.exception.NggRequestTimeoutException;
import com.nz.jnng.service.codec.ChannelMessageCodec;
import com.nz.jnng.service.communication.PairChannel;
import com.nz.jnng.service.communication.PubChannel;
import com.nz.jnng.service.communication.PullChannel;
import com.nz.jnng.service.communication.PushChannel;
import com.nz.jnng.service.communication.RepChannel;
import com.nz.jnng.service.communication.ReqChannel;
import com.nz.jnng.service.communication.SubChannel;
import com.nz.jnng.service.listener.ChannelConnectionState;
import com.nz.jnng.socket.NngSocketConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executable examples of the API used by applications. */
class JnngApplicationTest {
    private static final int DATA_ID = 100;
    private static final int STATUS_ID = 101;
    private static final int PING_ID = 200;
    private static final int PONG_ID = 201;

    @Test
    void pairDispatchesSeveralMessageTypesToOneListenerPerType() throws Exception {
        String address = address("pair");
        AtomicReference<Data> dataReceived = new AtomicReference<>();
        AtomicReference<Status> statusReceived = new AtomicReference<>();
        CountDownLatch messages = new CountDownLatch(2);
        CountDownLatch connected = new CountDownLatch(2);
        CountDownLatch disconnected = new CountDownLatch(1);

        try (Jnng agent = new Jnng(); Jnng overlay = new Jnng()) {
            PairChannel agentChannel = agent.pair(ChannelConfiguration.listen(address).build());
            PairChannel overlayChannel = overlay.pair(ChannelConfiguration.dial(address).build());

            Subscription dataListener = agentChannel.registerMessage(
                    DATA_ID, Data.class, dataCodec(), message -> {
                        dataReceived.set(message);
                        messages.countDown();
                    });
            Subscription statusListener = agentChannel.registerMessage(
                    STATUS_ID, Status.class, statusCodec(), message -> {
                        statusReceived.set(message);
                        messages.countDown();
                    });
            overlayChannel.registerMessage(DATA_ID, Data.class, dataCodec());
            overlayChannel.registerMessage(STATUS_ID, Status.class, statusCodec());

            agentChannel.onConnectionChanged(event -> {
                if (event.state() == ChannelConnectionState.CONNECTED) connected.countDown();
                if (event.state() == ChannelConnectionState.DISCONNECTED) disconnected.countDown();
            });
            overlayChannel.onConnectionChanged(event -> {
                if (event.state() == ChannelConnectionState.CONNECTED) connected.countDown();
            });

            agentChannel.open();
            overlayChannel.open();
            assertTrue(connected.await(3, TimeUnit.SECONDS));

            overlayChannel.send(new Data("frame-42"));
            overlayChannel.sendAsync(new Status("READY")).get(3, TimeUnit.SECONDS);

            assertTrue(messages.await(3, TimeUnit.SECONDS));
            assertEquals(new Data("frame-42"), dataReceived.get());
            assertEquals(new Status("READY"), statusReceived.get());
            assertTrue(dataListener.isActive());
            dataListener.close();
            assertFalse(dataListener.isActive());
            assertTrue(statusListener.isActive());

            overlayChannel.close();
            assertTrue(disconnected.await(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void exposesPubSubPushPullAndReqRepWithoutHidingTheirPatterns() throws Exception {
        try (Jnng server = new Jnng(); Jnng client = new Jnng()) {
            CountDownLatch statusReceived = new CountDownLatch(1);
            AtomicReference<Status> status = new AtomicReference<>();
            PubChannel pub = server.pub(ChannelConfiguration.listen(address("pubsub")).build());
            SubChannel sub = client.sub(ChannelConfiguration.dial(pub.configuration().address()).build());
            pub.registerMessage(STATUS_ID, Status.class, statusCodec());
            sub.registerMessage(STATUS_ID, Status.class, statusCodec(), value -> {
                status.set(value);
                statusReceived.countDown();
            });
            CountDownLatch subConnected = connectedLatch(sub);
            pub.open();
            sub.open();
            assertTrue(subConnected.await(3, TimeUnit.SECONDS));
            Thread.sleep(50); // NNG PUB/SUB subscription propagation.
            pub.publish(new Status("RECORDING"));
            assertTrue(statusReceived.await(3, TimeUnit.SECONDS));
            assertEquals(new Status("RECORDING"), status.get());

            CountDownLatch workReceived = new CountDownLatch(1);
            AtomicReference<Data> work = new AtomicReference<>();
            PullChannel pull = server.pull(ChannelConfiguration.listen(address("pipeline")).build());
            PushChannel push = client.push(ChannelConfiguration.dial(pull.configuration().address()).build());
            pull.registerMessage(DATA_ID, Data.class, dataCodec(), value -> {
                work.set(value);
                workReceived.countDown();
            });
            push.registerMessage(DATA_ID, Data.class, dataCodec());
            pull.open();
            push.open();
            push.push(new Data("job"));
            assertTrue(workReceived.await(3, TimeUnit.SECONDS));
            assertEquals(new Data("job"), work.get());

            RepChannel rep = server.rep(ChannelConfiguration.listen(address("reqrep")).build());
            ReqChannel req = client.req(ChannelConfiguration.dial(rep.configuration().address()).build());
            rep.registerMessage(PONG_ID, Pong.class, pongCodec());
            rep.registerRequest(PING_ID, Ping.class, pingCodec(),
                    ping -> new Pong("reply:" + ping.value()));
            req.registerMessage(PING_ID, Ping.class, pingCodec());
            req.registerMessage(PONG_ID, Pong.class, pongCodec());
            assertThrows(IllegalArgumentException.class, () ->
                    req.registerMessage(PONG_ID, Status.class, statusCodec()));
            rep.open();
            req.open();
            assertEquals(new Pong("reply:hello"),
                    req.request(new Ping("hello"), Pong.class));
        }
    }

    @Test
    void appliesLifecycleRulesAndRequestTimeouts() {
        String address = address("timeout");
        NngSocketConfig timeoutConfig = NngSocketConfig.defaults()
                .withRequestTimeout(Duration.ofMillis(50));

        try (Jnng server = new Jnng(); Jnng client = new Jnng()) {
            RepChannel rep = server.rep(ChannelConfiguration.listen(address).build());
            ReqChannel req = client.req(ChannelConfiguration.dial(address)
                    .socketConfig(timeoutConfig).build());
            rep.registerMessage(PONG_ID, Pong.class, pongCodec());
            rep.registerRequest(PING_ID, Ping.class, pingCodec(), ping -> {
                Thread.sleep(200);
                return new Pong("late");
            });
            req.registerMessage(PING_ID, Ping.class, pingCodec());
            req.registerMessage(PONG_ID, Pong.class, pongCodec());
            rep.open();
            req.open();

            assertThrows(NggRequestTimeoutException.class,
                    () -> req.request(new Ping("timeout"), Pong.class));
            assertThrows(IllegalStateException.class, rep::open);
            assertThrows(IllegalStateException.class, () ->
                    req.registerMessage(300, Data.class, dataCodec()));
        }
    }

    private static CountDownLatch connectedLatch(AbstractChannel channel) {
        CountDownLatch latch = new CountDownLatch(1);
        channel.onConnectionChanged(event -> {
            if (event.state() == ChannelConnectionState.CONNECTED) latch.countDown();
        });
        return latch;
    }

    private static String address(String name) {
        return "inproc://new-api-" + name + '-' + UUID.randomUUID();
    }

    private static ChannelMessageCodec<Data> dataCodec() {
        return stringCodec(Data::new, Data::value);
    }

    private static ChannelMessageCodec<Status> statusCodec() {
        return stringCodec(Status::new, Status::value);
    }

    private static ChannelMessageCodec<Ping> pingCodec() {
        return stringCodec(Ping::new, Ping::value);
    }

    private static ChannelMessageCodec<Pong> pongCodec() {
        return stringCodec(Pong::new, Pong::value);
    }

    private static <T> ChannelMessageCodec<T> stringCodec(
            java.util.function.Function<String, T> decoder,
            java.util.function.Function<T, String> encoder
    ) {
        return new ChannelMessageCodec<>() {
            @Override
            public byte[] encode(T message) {
                return encoder.apply(message).getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public T decode(byte[] payload) {
                return decoder.apply(new String(payload, StandardCharsets.UTF_8));
            }
        };
    }

    private record Data(String value) {}
    private record Status(String value) {}
    private record Ping(String value) {}
    private record Pong(String value) {}
}
