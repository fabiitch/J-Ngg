import com.nz.jngg.impl.PairSocket;
import com.nz.jngg.utils.Nng;
import com.nz.jnng.nng_h;
import com.nz.jnng.nng_socket;

public class PairTest {

    public static void main(String[] args) throws Exception {
        Nng.load();
        System.out.println(
                "NNG Version = " +
                        nng_h.nng_version().getString(0)
        );
        Thread server = new Thread(() -> {

            try (var socket = new PairSocket()) {
                System.out.println(
                        "socket id =" + nng_socket.id(socket.getSocket())
                );
                socket.listen("inproc://pair-test");

                System.out.println("Server waiting...");

                String message = socket.receiveString();

                System.out.println("Server received: " + message);

                socket.send("pong");
            }
        });

        Thread client = new Thread(() -> {

            try {

                Thread.sleep(1000);

                try (var socket = new PairSocket()) {

                    socket.dial("inproc://pair-test");

                    socket.send("ping");

                    String response = socket.receiveString();

                    System.out.println("Client received: " + response);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        server.start();
        client.start();

        server.join();
        client.join();
    }
}
