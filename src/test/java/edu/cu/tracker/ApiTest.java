package edu.cu.tracker;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/** Starts the real HTTP server on a free port with the memory store and calls the API. */
class ApiTest {

    private static App app;
    private static String base;
    private static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws Exception {
        Config config = new Config("unit-test", 0, "memory", "localhost", 6379, null, "test");
        app = new App(config, new MemoryStore());
        app.start();
        base = "http://localhost:" + app.port();
    }

    @AfterAll
    static void stop() {
        app.stop();
    }

    private static HttpResponse<String> call(String method, String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (json == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthIsUp() throws Exception {
        HttpResponse<String> r = call("GET", "/health", null);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"status\":\"UP\""));
    }

    @Test
    void createReadAndReview() throws Exception {
        HttpResponse<String> created = call("POST", "/api/submissions",
                "{\"uid\":\"24MCC20081\",\"studentName\":\"Iqbal\",\"title\":\"Tracker\","
                        + "\"repoUrl\":\"https://github.com/iPatan7/project-submission-tracker\"}");
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("\"status\":\"SUBMITTED\""));

        String id = created.body().replaceAll(".*\"id\":(\\d+).*", "$1");
        assertEquals(200, call("GET", "/api/submissions/" + id, null).statusCode());

        HttpResponse<String> moved = call("PUT", "/api/submissions/" + id + "/status", "{\"status\":\"UNDER_REVIEW\"}");
        assertEquals(200, moved.statusCode());
        assertTrue(moved.body().contains("UNDER_REVIEW"));
    }

    @Test
    void badInputGives400() throws Exception {
        assertEquals(400, call("POST", "/api/submissions", "{\"uid\":\"abc\"}").statusCode());
        assertEquals(400, call("POST", "/api/submissions", "not json").statusCode());
    }

    @Test
    void wrongStatusJumpGives409() throws Exception {
        HttpResponse<String> created = call("POST", "/api/submissions",
                "{\"uid\":\"24MCC20082\",\"studentName\":\"B\",\"title\":\"T\",\"repoUrl\":\"https://github.com/b/t\"}");
        String id = created.body().replaceAll(".*\"id\":(\\d+).*", "$1");
        assertEquals(409, call("PUT", "/api/submissions/" + id + "/status", "{\"status\":\"APPROVED\"}").statusCode());
    }

    @Test
    void unknownIdGives404() throws Exception {
        assertEquals(404, call("GET", "/api/submissions/9999", null).statusCode());
    }
}
