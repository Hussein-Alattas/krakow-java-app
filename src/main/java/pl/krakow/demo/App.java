package pl.krakow.demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class App {

    static final String VERSION = "1.1.0";

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        byte[] video = loadVideo();
        HttpServer server = createServer(port, video);
        server.start();
        System.out.println("Krakow video " + VERSION + " on port " + port + ", video bytes: " + video.length);
    }

    static byte[] loadVideo() throws IOException {
        try (InputStream in = App.class.getResourceAsStream("/videos/krakow.mp4")) {
            return in == null ? new byte[0] : in.readAllBytes();
        }
    }

    static HttpServer createServer(int port, byte[] video) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/health", ex -> sendText(ex, 200, "application/json", "{\"status\":\"DOWN\"}"));
        server.createContext("/api/info", ex -> sendText(ex, 200, "application/json",
                "{\"version\":\"" + VERSION + "\",\"videoBytes\":" + video.length + "}"));
        server.createContext("/video", ex -> streamVideo(ex, video));
        server.createContext("/", ex -> {
            if ("/".equals(ex.getRequestURI().getPath())) sendText(ex, 200, "text/html; charset=utf-8", page());
            else sendText(ex, 404, "application/json", "{\"error\":\"not found\"}");
        });
        return server;
    }

    static String page() {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>Krak\u00f3w</title>"
             + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
             + "<style>html,body{margin:0;height:100%;background:#000}"
             + "video{width:100%;height:100%;object-fit:contain}</style></head>"
             + "<body><video src=\"/video\" autoplay muted loop playsinline controls></video></body></html>";
    }

    /** Returns {start, end} (inclusive) or null if the header is not valid for this file size. */
    static long[] parseRange(String header, long size) {
        if (header == null || !header.startsWith("bytes=") || header.contains(",")) return null;
        String spec = header.substring(6).trim();
        int dash = spec.indexOf('-');
        if (dash < 0) return null;
        String a = spec.substring(0, dash).trim();
        String b = spec.substring(dash + 1).trim();
        try {
            long start, end;
            if (a.isEmpty()) {                       // bytes=-N : the last N bytes
                long n = Long.parseLong(b);
                if (n <= 0) return null;
                start = Math.max(0, size - n);
                end = size - 1;
            } else {
                start = Long.parseLong(a);
                end = b.isEmpty() ? size - 1 : Math.min(Long.parseLong(b), size - 1);
            }
            if (start < 0 || start >= size || end < start) return null;
            return new long[]{start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static void streamVideo(HttpExchange ex, byte[] video) throws IOException {
        if (video.length == 0) {
            sendText(ex, 404, "application/json", "{\"error\":\"video missing\"}");
            return;
        }
        ex.getResponseHeaders().set("Content-Type", "video/mp4");
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");
        String range = ex.getRequestHeaders().getFirst("Range");
        try {
            if (range == null) {
                ex.sendResponseHeaders(200, video.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(video); }
                return;
            }
            long[] r = parseRange(range, video.length);
            if (r == null) {
                ex.getResponseHeaders().set("Content-Range", "bytes */" + video.length);
                ex.sendResponseHeaders(416, -1);
                ex.close();
                return;
            }
            int len = (int) (r[1] - r[0] + 1);
            ex.getResponseHeaders().set("Content-Range", "bytes " + r[0] + "-" + r[1] + "/" + video.length);
            ex.sendResponseHeaders(206, len);
            try (OutputStream os = ex.getResponseBody()) { os.write(video, (int) r[0], len); }
        } catch (IOException ignored) {
            // the browser closed the connection (normal while seeking)
        } finally {
            ex.close();
        }
    }

    private static void sendText(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
