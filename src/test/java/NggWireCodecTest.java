import com.nz.jngg.NggHeader;
import com.nz.jngg.NggMessage;
import com.nz.jngg.NggRequestClient;
import com.nz.jngg.NggRequestServer;
import com.nz.jngg.NngSocketConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NggWireCodecTest {
    @Test
    void requestAndResponseCarryTheirHeader() throws Exception {
        String address = "inproc://framed-request-test";
        NngSocketConfig config = NngSocketConfig.defaults()
                .withRequestTimeout(Duration.ofSeconds(5))
                .withReconnectInterval(Duration.ofMillis(100))
                .withMaxPendingRequestsPerPeer(4);
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();

        try (NggRequestServer server = new NggRequestServer(config)) {
            server.listen(address);
            Thread serverThread = Thread.ofPlatform().start(() -> {
                try {
                    NggMessage request = server.receive();
                    assertEquals((short) 7, request.header().messageType());
                    assertEquals(4, request.header().payloadLength());
                    assertArrayEquals("ping".getBytes(StandardCharsets.UTF_8), request.payload());
                    server.reply(request, (short) 8, "pong".getBytes(StandardCharsets.UTF_8));
                } catch (Throwable failure) {
                    serverFailure.set(failure);
                }
            });

            try (NggRequestClient client = new NggRequestClient(config)) {
                client.connect(address);
                NggMessage response = client.request(
                        (short) 7, "ping".getBytes(StandardCharsets.UTF_8));
                assertEquals(new NggHeader(4, (short) 8, 1), response.header());
                assertArrayEquals("pong".getBytes(StandardCharsets.UTF_8), response.payload());
            }

            serverThread.join();
            assertNull(serverFailure.get());
        }
    }
}
