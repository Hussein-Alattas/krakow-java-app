package pl.krakow.demo;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    private final byte[] video = new byte[1000];
    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws Exception {
        for (int i = 0; i < video.length; i++) video[i] = (byte) i;
        server = App.createServer(0, video);
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpResponse<byte[]> get(String path, String range) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (range != null) b.header("Range", range);
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void healthReturnsUp() throws Exception {
        HttpResponse<byte[]> r = get("/health", null);
        assertEquals(200, r.statusCode());
        assertTrue(new String(r.body()).contains("UP"));
    }

    @Test
    void infoContainsVersion() throws Exception {
        assertTrue(new String(get("/api/info", null).body()).contains(App.VERSION));
    }

    @Test
    void fullVideoIs200() throws Exception {
        HttpResponse<byte[]> r = get("/video", null);
        assertEquals(200, r.statusCode());
        assertEquals(1000, r.body().length);
    }

    @Test
    void rangeRequestIs206() throws Exception {
        HttpResponse<byte[]> r = get("/video", "bytes=0-99");
        assertEquals(206, r.statusCode());
        assertEquals("bytes 0-99/1000", r.headers().firstValue("Content-Range").orElse(""));
        assertEquals(100, r.body().length);
    }

    @Test
    void middleRangeReturnsRightBytes() throws Exception {
        HttpResponse<byte[]> r = get("/video", "bytes=500-509");
        assertEquals(206, r.statusCode());
        byte[] expected = new byte[10];
        System.arraycopy(video, 500, expected, 0, 10);
        assertArrayEquals(expected, r.body());
    }

    @Test
    void rangeBeyondEndIs416() throws Exception {
        assertEquals(416, get("/video", "bytes=5000-6000").statusCode());
    }

    @Test
    void parseRangeCases() {
        assertArrayEquals(new long[]{10, 999}, App.parseRange("bytes=10-", 1000));
        assertArrayEquals(new long[]{900, 999}, App.parseRange("bytes=-100", 1000));
        assertNull(App.parseRange("bytes=abc-", 1000));
        assertNull(App.parseRange("items=0-1", 1000));
    }

    @Test
    void unknownPathIs404() throws Exception {
        assertEquals(404, get("/nope", null).statusCode());
    }
}
