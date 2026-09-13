package com.fabiitch.jnng.graalvm;

import com.fabiitch.jnng.constants.NngErrorCode;
import com.fabiitch.jnng.exception.NngException;
import com.fabiitch.jnng.service.Jnng;
import com.fabiitch.jnng.service.channel.AbstractChannel;
import com.fabiitch.jnng.service.channel.AbstractReceivingChannel;
import com.fabiitch.jnng.service.channel.ChannelConfiguration;
import com.fabiitch.jnng.service.codec.ChannelMessageCodec;
import com.fabiitch.jnng.service.communication.PairChannel;
import com.fabiitch.jnng.service.communication.PubChannel;
import com.fabiitch.jnng.service.communication.PullChannel;
import com.fabiitch.jnng.service.communication.PushChannel;
import com.fabiitch.jnng.service.communication.RepChannel;
import com.fabiitch.jnng.service.communication.ReqChannel;
import com.fabiitch.jnng.service.communication.SubChannel;
import com.fabiitch.jnng.service.listener.ChannelConnectionState;
import com.fabiitch.jnng.socket.NativeMessage;
import com.fabiitch.jnng.socket.impl.Pair1Socket;
import com.fabiitch.jnng.socket.impl.SubSocket;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Runtime scenario used by the GraalVM tracing agent.
 *
 * <p>It deliberately exercises every NNG pattern and every low-level native
 * operation reachable from the public J-Nng implementation. This is not a
 * benchmark and does not replace the unit test suite.</p>
 */
public final class GraalVmNativeMetadataApp {
    private static final int TEXT_ID = 1;
    private static final int REQUEST_ID = 2;
    private static final int RESPONSE_ID = 3;
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final ChannelMessageCodec<Message> CODEC = new ChannelMessageCodec<>() {
        @Override
        public byte[] encode(Message message) {
            return message.value().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Message decode(byte[] payload) {
            return new Message(new String(payload, StandardCharsets.UTF_8));
        }
    };

    private GraalVmNativeMetadataApp() {
    }

    public static void main(String[] args) throws Exception {
        exerciseNativeMessagesAndErrors();
        exerciseSubscriptionRemoval();
        exercisePair();
        exercisePubSub();
        exercisePushPull();
        exerciseReqRep();
        exerciseReceiveTimeoutAndCancellation();
        System.out.println("GraalVM metadata scenario completed successfully.");
    }

    private static void exerciseNativeMessagesAndErrors() {
        byte[] expected = "native-message".getBytes(StandardCharsets.UTF_8);
        try (NativeMessage message = NativeMessage.copyOf(expected)) {
            require(message.size() == expected.length, "Unexpected native message size");
            require(java.util.Arrays.equals(expected, message.toByteArray()),
                    "Unexpected native message body");
        }

        // Forces the nng_strerror downcall without failing the scenario.
        require(!new NngException(NngErrorCode.EINVAL).getMessage().isBlank(),
                "NNG did not return an error description");
    }

    private static void exerciseSubscriptionRemoval() {
        // SubChannel subscribes to the empty prefix. The raw socket additionally
        // covers nng_sub0_socket_unsubscribe, which has no high-level counterpart.
        try (SubSocket socket = new SubSocket()) {
            byte[] prefix = "metadata".getBytes(StandardCharsets.UTF_8);
            socket.subscribe(prefix);
            socket.unsubscribe(prefix);
        }
    }

    private static void exercisePair() throws Exception {
        String address = address("pair");
        CountDownLatch connected = new CountDownLatch(2);
        CountDownLatch received = new CountDownLatch(3);

        try (Jnng leftOwner = new Jnng(); Jnng rightOwner = new Jnng()) {
            PairChannel left = leftOwner.pair(ChannelConfiguration.listen(address).build());
            PairChannel right = rightOwner.pair(ChannelConfiguration.dial(address).build());
            registerReceiver(left, received);
            registerReceiver(right, received);
            watchConnection(left, connected);
            watchConnection(right, connected);
            left.open();
            right.open();
            await(connected, "PAIR connection");

            left.send(new Message("sync"));
            require(right.trySend(new Message("non-blocking")), "PAIR trySend failed");
            left.sendAsync(new Message("async")).get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            await(received, "PAIR messages");
        }
    }

    private static void exercisePubSub() throws Exception {
        String address = address("pubsub");
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch received = new CountDownLatch(3);

        try (Jnng publisherOwner = new Jnng(); Jnng subscriberOwner = new Jnng()) {
            PubChannel publisher = publisherOwner.pub(
                    ChannelConfiguration.listen(address).build());
            SubChannel subscriber = subscriberOwner.sub(
                    ChannelConfiguration.dial(address).build());
            publisher.registerMessage(TEXT_ID, Message.class, CODEC);
            registerReceiver(subscriber, received);
            watchConnection(subscriber, connected);
            publisher.open();
            subscriber.open();
            await(connected, "PUB/SUB connection");
            Thread.sleep(100); // Allow the subscription to reach the publisher.

            publisher.publish(new Message("sync"));
            require(publisher.tryPublish(new Message("non-blocking")),
                    "PUB tryPublish failed");
            publisher.publishAsync(new Message("async"))
                    .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            await(received, "PUB/SUB messages");
        }
    }

    private static void exercisePushPull() throws Exception {
        String address = address("pipeline");
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch received = new CountDownLatch(3);

        try (Jnng producerOwner = new Jnng(); Jnng consumerOwner = new Jnng()) {
            PullChannel consumer = consumerOwner.pull(
                    ChannelConfiguration.listen(address).build());
            PushChannel producer = producerOwner.push(
                    ChannelConfiguration.dial(address).build());
            registerReceiver(consumer, received);
            producer.registerMessage(TEXT_ID, Message.class, CODEC);
            watchConnection(producer, connected);
            consumer.open();
            producer.open();
            await(connected, "PUSH/PULL connection");

            producer.push(new Message("sync"));
            require(producer.tryPush(new Message("non-blocking")), "PUSH tryPush failed");
            producer.pushAsync(new Message("async"))
                    .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            await(received, "PUSH/PULL messages");
        }
    }

    private static void exerciseReqRep() throws Exception {
        String address = address("reqrep");
        CountDownLatch connected = new CountDownLatch(1);

        try (Jnng serverOwner = new Jnng(); Jnng clientOwner = new Jnng()) {
            RepChannel server = serverOwner.rep(ChannelConfiguration.listen(address).build());
            ReqChannel client = clientOwner.req(ChannelConfiguration.dial(address).build());
            server.registerMessage(RESPONSE_ID, Message.class, CODEC);
            server.registerRequest(REQUEST_ID, Request.class, requestCodec(),
                    request -> new Message("reply:" + request.value()));
            client.registerMessage(REQUEST_ID, Request.class, requestCodec());
            client.registerMessage(RESPONSE_ID, Message.class, CODEC);
            watchConnection(client, connected);
            server.open();
            client.open();
            await(connected, "REQ/REP connection");

            Message response = client.requestAsync(
                            new Request("metadata"), Message.class, WAIT)
                    .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            require(response.equals(new Message("reply:metadata")),
                    "Unexpected REQ/REP response");
        }
    }

    private static void exerciseReceiveTimeoutAndCancellation() throws Exception {
        try (Pair1Socket socket = new Pair1Socket()) {
            try {
                socket.receiveNativeAsync(Duration.ofMillis(25))
                        .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
                throw new IllegalStateException("The unconnected receive should time out");
            } catch (ExecutionException expected) {
                require(expected.getCause() instanceof NngException,
                        "Unexpected receive timeout: " + expected.getCause());
            }

            CompletableFuture<NativeMessage> cancelled =
                    socket.receiveNativeAsync(Duration.ofSeconds(30));
            require(cancelled.cancel(true), "Receive cancellation failed");
        }
    }

    private static void registerReceiver(
            AbstractReceivingChannel channel,
            CountDownLatch received
    ) {
        channel.registerMessage(TEXT_ID, Message.class, CODEC, ignored -> received.countDown());
    }

    private static void watchConnection(AbstractChannel channel, CountDownLatch connected) {
        channel.onConnectionChanged(event -> {
            if (event.state() == ChannelConnectionState.CONNECTED) connected.countDown();
        });
    }

    private static ChannelMessageCodec<Request> requestCodec() {
        return new ChannelMessageCodec<>() {
            @Override
            public byte[] encode(Request message) {
                return message.value().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Request decode(byte[] payload) {
                return new Request(new String(payload, StandardCharsets.UTF_8));
            }
        };
    }

    private static void await(CountDownLatch latch, String operation)
            throws InterruptedException {
        if (!latch.await(WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("Timed out waiting for " + operation);
        }
    }

    private static String address(String name) {
        return "inproc://graalvm-metadata-" + name + '-' + UUID.randomUUID();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Message(String value) {
    }

    private record Request(String value) {
    }
}
