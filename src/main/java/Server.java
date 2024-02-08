import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Server {
    final List<String> validPaths =
            List.of("/index.html",
                    "/spring.svg",
                    "/spring.png",
                    "/resources.html",
                    "/styles.css",
                    "/app.js",
                    "/links.html",
                    "/forms.html",
                    "/classic.html",
                    "/events.html",
                    "/events.js");

    private final int threadPoolSize = 64;
    private ExecutorService service = Executors.newFixedThreadPool(threadPoolSize);

    public void listen(int port) {
        try (final var serverSocket = new ServerSocket(port)) {
            Future<Object> result = service.submit(() -> {
                while (true) {
                    System.out.println("Waiting for connection...");
                    try (final var socket = serverSocket.accept()) {
                        process(socket);
                    }
                }
            });
            result.get();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void process(Socket socket) throws IOException {
        System.out.println("process...");
        final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        final var out = new BufferedOutputStream(socket.getOutputStream());
        final var requestLine = in.readLine();
        final var parts = requestLine.split(" ");

        if (parts.length != 3) {
            // just close socket
            return;
        }

        final var path = parts[1];
        if (!validPaths.contains(path)) {
            out.write(("HTTP/1.1 404 Not Found\r\n"
            + "Content-Length: 0\r\n"
            + "Connection: close"
            + "\r\n").getBytes());
            out.flush();
            return;
        }
        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);

        // special case for classic
        if (path.equals("/classic.html")) {
            final var template = Files.readString(filePath);
            final var content = template.replace(
                    "{time}",
                    LocalDateTime.now().toString()
            ).getBytes();
            out.write((
                    "HTTP/1.1 200 OK\r\r" +
                            "Content-Type:" + mimeType + "\r\n" +
                            "Content-Length:" + content.length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.write(content);
            out.flush();
            return;
        }

        final var length = Files.size(filePath);
        out.write((
                "HTTP/1.1 200 OK " +
                        "Content-Type:" + mimeType + "\r\n" +
                        "Content-Length:" + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }
}
