import com.nz.jngg.message.NggMessage;
import com.nz.jngg.NggRequestClient;
import com.nz.jngg.NggRequestServer;
import com.nz.jngg.NngSocketConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductUseCaseTest {
    private static final short PING = 1;
    private static final short PONG = 2;

    private static final String AGENT_ADDRESS = "inproc://product-agent";
    private static final String RECORDER_ADDRESS = "inproc://product-recorder";
    private static final String WORKER_ADDRESS = "inproc://product-worker";

    private static final NngSocketConfig CONFIG = NngSocketConfig.defaults()
            .withRequestTimeout(Duration.ofSeconds(5))
            .withReconnectInterval(Duration.ofMillis(100))
            .withMaxPendingRequestsPerPeer(8);

    @Test
    void productProcessesExchangePingPongWithRequestIds() throws Exception {
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();

        try (NggRequestServer agent = new NggRequestServer(CONFIG);
             NggRequestServer recorder = new NggRequestServer(CONFIG);
             NggRequestServer worker = new NggRequestServer(CONFIG)) {

            agent.listen(AGENT_ADDRESS);
            recorder.listen(RECORDER_ADDRESS);
            worker.listen(WORKER_ADDRESS);

            // Agent is the server for Recorder, Overlay, BackOffice and Worker.
            Thread agentThread = serve(agent, "Agent", 4, serverFailure);

            // Recorder is the server for Overlay and BackOffice.
            Thread recorderThread = serve(recorder, "Recorder", 2, serverFailure);

            // Worker is a server called by BackOffice.
            Thread workerThread = serve(worker, "Worker", 1, serverFailure);

            List<Exchange> exchanges = new ArrayList<>();
            exchanges.add(ping("Recorder", "Agent", AGENT_ADDRESS));
            exchanges.add(ping("Overlay", "Agent", AGENT_ADDRESS));
            exchanges.add(ping("BackOffice", "Agent", AGENT_ADDRESS));
            exchanges.add(ping("Worker", "Agent", AGENT_ADDRESS));
            exchanges.add(ping("Overlay", "Recorder", RECORDER_ADDRESS));
            exchanges.add(ping("BackOffice", "Recorder", RECORDER_ADDRESS));
            exchanges.add(ping("BackOffice", "Worker", WORKER_ADDRESS));

            agentThread.join();
            recorderThread.join();
            workerThread.join();

            if (serverFailure.get() != null) {
                throw new AssertionError("A product server failed", serverFailure.get());
            }

            assertEquals(7, exchanges.size());
            for (Exchange exchange : exchanges) {
                assertEquals(1L, exchange.requestId());
                assertEquals(PONG, exchange.responseType());
                assertEquals("PONG " + exchange.target() + " -> " + exchange.source(),
                        exchange.responsePayload());
            }
        }
    }

    private static Thread serve(
            NggRequestServer server,
            String serverName,
            int requestCount,
            AtomicReference<Throwable> failure
    ) {
        return Thread.ofPlatform().name(serverName + "-server").start(() -> {
            try {
                for (int index = 0; index < requestCount; index++) {
                    NggMessage request = server.receive();
                    String payload = text(request.payload());

                    assertEquals(PING, request.header().messageType());
                    assertTrue(request.header().requestId() > 0);
                    assertTrue(payload.startsWith("PING "));
                    assertTrue(payload.endsWith(" -> " + serverName));

                    String source = payload.substring(
                            "PING ".length(), payload.length() - (" -> " + serverName).length());
                    server.reply(request, PONG, bytes("PONG " + serverName + " -> " + source));
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        });
    }

    private static Exchange ping(String source, String target, String address) throws Exception {
        try (NggRequestClient client = new NggRequestClient(CONFIG)) {
            client.connect(address);
            NggMessage response = client.request(PING, bytes("PING " + source + " -> " + target));

            assertEquals(PONG, response.header().messageType());
            assertEquals(response.payload().length, response.header().payloadLength());
            assertTrue(response.header().requestId() > 0);

            return new Exchange(source, target, response.header().requestId(),
                    response.header().messageType(), text(response.payload()));
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    private record Exchange(
            String source,
            String target,
            long requestId,
            short responseType,
            String responsePayload
    ) {
    }
}
