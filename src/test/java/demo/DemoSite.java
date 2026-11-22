package demo;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;

/**
 * Serves the static demo trading site from {@code src/test/resources/site} on a random local port.
 * Extension-less URLs map to {@code .html} files, so {@code /blotter/48213} serves {@code blotter/48213.html}.
 */
public final class DemoSite {
    private static String baseUrl;

    private DemoSite() {
    }

    public static synchronized String baseUrl() {
        if (baseUrl == null) {
            baseUrl = start();
        }
        return baseUrl;
    }

    private static String start() {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                final String path = exchange.getRequestURI().getPath().replaceFirst("^/+", "");
                byte[] body = read("site/" + path);
                if (body == null) {
                    body = read("site/" + path + ".html");
                }
                if (body == null || path.contains("..")) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type",
                        path.endsWith(".js") ? "text/javascript" : "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            server.setExecutor(null);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
            return "http://127.0.0.1:" + server.getAddress().getPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] read(final String resource) throws IOException {
        if (resource.endsWith("/")) {
            return null;
        }
        try (InputStream in = DemoSite.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        }
    }
}
