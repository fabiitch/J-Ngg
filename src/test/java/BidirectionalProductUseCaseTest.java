import com.nz.jngg.NggMessage;
import com.nz.jngg.NggPeerChannel;
import com.nz.jngg.NngSocketConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class BidirectionalProductUseCaseTest {
    private static final short PING = 1;
    private static final short PONG = 2;

    private static final NngSocketConfig CONFIG = NngSocketConfig.defaults()
            .withRequestTimeout(Duration.ofSeconds(5))
            .withReconnectInterval(Duration.ofMillis(100))
            .withMaxPendingRequestsPerPeer(8);

    @Test
    void displaysEveryBidirectionalPingPong() {
        log.info("=== Starting product topology ===");

        List<LoggedChannel> channels = List.of(
                channel("Agent", "Recorder", "inproc://logged-agent-recorder"),
                channel("Agent", "Overlay", "inproc://logged-agent-overlay"),
                channel("Agent", "BackOffice", "inproc://logged-agent-backoffice"),
                channel("Agent", "Worker", "inproc://logged-agent-worker"),
                channel("Recorder", "Overlay", "inproc://logged-recorder-overlay"),
                channel("Recorder", "BackOffice", "inproc://logged-recorder-backoffice"),
                channel("Worker", "BackOffice", "inproc://logged-worker-backoffice")
        );

        try {
            channels.forEach(LoggedChannel::pingPongBothWays);
            log.info("=== 7 channels, 14 successful ping/pong transactions ===");
        } finally {
            channels.forEach(LoggedChannel::close);
            log.info("=== Product topology closed ===");
        }
    }

    private static LoggedChannel channel(String listenerName, String dialerName, String address) {
        NggPeerChannel listener = new NggPeerChannel(CONFIG);
        NggPeerChannel dialer = new NggPeerChannel(CONFIG);
        listener.listen(address);
        dialer.connect(address);
        log.info("OPEN  {} <-> {} ({})", listenerName, dialerName, address);
        return new LoggedChannel(listenerName, listener, dialerName, dialer);
    }

    private static void exchange(
            String requesterName,
            NggPeerChannel requester,
            String responderName,
            NggPeerChannel responder
    ) {
        String ping = "PING " + requesterName + " -> " + responderName;
        long requestId = requester.sendRequest(PING, bytes(ping));
        log.info("SEND  id={} type=PING {} -> {}", requestId, requesterName, responderName);

        NggMessage request = responder.receive();
        assertEquals(requestId, request.header().requestId());
        assertEquals(PING, request.header().messageType());
        assertEquals(ping, text(request.payload()));
        log.info("RECV  id={} type=PING {} <- {}", requestId, responderName, requesterName);

        String pong = "PONG " + responderName + " -> " + requesterName;
        responder.reply(request, PONG, bytes(pong));
        log.info("SEND  id={} type=PONG {} -> {}", requestId, responderName, requesterName);

        NggMessage response = requester.receive();
        requester.completeResponse(requestId, response);
        assertEquals(requestId, response.header().requestId());
        assertEquals(PONG, response.header().messageType());
        assertEquals(pong, text(response.payload()));
        log.info("RECV  id={} type=PONG {} <- {}", requestId, requesterName, responderName);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    private record LoggedChannel(
            String listenerName,
            NggPeerChannel listener,
            String dialerName,
            NggPeerChannel dialer
    ) implements AutoCloseable {
        void pingPongBothWays() {
            exchange(listenerName, listener, dialerName, dialer);
            exchange(dialerName, dialer, listenerName, listener);
        }

        @Override
        public void close() {
            dialer.close();
            listener.close();
            log.info("CLOSE {} <-> {}", listenerName, dialerName);
        }
    }
}
