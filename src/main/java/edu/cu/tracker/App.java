package edu.cu.tracker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;

/**
 * Project submission tracker.
 *
 *   GET  /health                          health of the app and its store
 *   GET  /api/submissions                 list all submissions
 *   POST /api/submissions                 new submission {uid, studentName, title, repoUrl}
 *   GET  /api/submissions/{id}            one submission
 *   PUT  /api/submissions/{id}/status     change status {status}
 *   GET  /                                simple HTML page with all submissions
 */
public class App {

    private final Config config;
    private final SubmissionStore store;
    private final SubmissionService service;
    private final HttpServer server;
    private final Gson gson = new Gson();

    public App(Config config, SubmissionStore store) throws IOException {
        this.config = config;
        this.store = store;
        this.service = new SubmissionService(store, Clock.systemUTC());
        this.server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/health", this::health);
        server.createContext("/api/submissions", this::submissions);
        server.createContext("/", this::home);
        server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public static void main(String[] args) throws IOException {
        Config config = Config.fromEnv();
        SubmissionStore store = "memory".equalsIgnoreCase(config.storeType())
                ? new MemoryStore()
                : new RedisStore(config.redisHost(), config.redisPort(), config.redisPassword());
        App app = new App(config, store);
        app.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("shutting down");
            app.stop();
        }));
        System.out.printf("submission-tracker %s started on port %d (env=%s, store=%s)%n",
                config.version(), app.port(), config.env(), store.name());
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void health(HttpExchange ex) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        int code;
        try {
            store.ping();
            body.put("status", "UP");
            code = 200;
        } catch (RuntimeException e) {
            body.put("status", "DOWN");
            body.put("error", e.getMessage());
            code = 503;
        }
        body.put("env", config.env());
        body.put("version", config.version());
        body.put("store", store.name());
        sendJson(ex, code, body);
    }

    private void submissions(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String[] parts = ex.getRequestURI().getPath().replaceAll("/+$", "").split("/");
        // parts: ["", "api", "submissions", id?, "status"?]
        try {
            if (parts.length == 3 && method.equals("GET")) {
                sendJson(ex, 200, service.list());
            } else if (parts.length == 3 && method.equals("POST")) {
                JsonObject in = readJson(ex);
                Submission s = service.submit(text(in, "uid"), text(in, "studentName"),
                        text(in, "title"), text(in, "repoUrl"));
                sendJson(ex, 201, s);
            } else if (parts.length == 4 && method.equals("GET")) {
                sendJson(ex, 200, service.get(parseId(parts[3])));
            } else if (parts.length == 5 && parts[4].equals("status") && method.equals("PUT")) {
                JsonObject in = readJson(ex);
                String status = text(in, "status");
                if (status == null) {
                    throw new IllegalArgumentException("status is required");
                }
                sendJson(ex, 200, service.changeStatus(parseId(parts[3]), status));
            } else {
                sendJson(ex, 404, Map.of("error", "no route for " + method + " " + ex.getRequestURI().getPath()));
            }
        } catch (IllegalArgumentException | JsonParseException e) {
            sendJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (NoSuchElementException e) {
            sendJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            sendJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            sendJson(ex, 500, Map.of("error", "internal error"));
            e.printStackTrace();
        }
    }

    private void home(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/")) {
            sendJson(ex, 404, Map.of("error", "not found"));
            return;
        }
        StringBuilder rows = new StringBuilder();
        for (Submission s : service.list()) {
            rows.append("<tr><td>").append(s.id())
                .append("</td><td>").append(esc(s.uid()))
                .append("</td><td>").append(esc(s.studentName()))
                .append("</td><td>").append(esc(s.title()))
                .append("</td><td>").append(s.status())
                .append("</td><td>").append(s.updatedAt())
                .append("</td></tr>\n");
        }
        String html = """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>Project Submission Tracker</title>
                <style>body{font-family:sans-serif;margin:30px} table{border-collapse:collapse}
                td,th{border:1px solid #999;padding:5px 10px}</style></head>
                <body><h2>Project Submission Tracker</h2>
                <p>Environment: %s | Version: %s | Store: %s</p>
                <table><tr><th>ID</th><th>UID</th><th>Student</th><th>Project</th><th>Status</th><th>Last update (UTC)</th></tr>
                %s</table></body></html>
                """.formatted(esc(config.env()), esc(config.version()), store.name(), rows);
        send(ex, 200, "text/html; charset=utf-8", html);
    }

    private JsonObject readJson(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject obj = gson.fromJson(body, JsonObject.class);
        if (obj == null) {
            throw new IllegalArgumentException("request body must be JSON");
        }
        return obj;
    }

    private static String text(JsonObject obj, String field) {
        return obj.has(field) && !obj.get(field).isJsonNull() ? obj.get(field).getAsString() : null;
    }

    private static long parseId(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("id must be a number");
        }
    }

    private void sendJson(HttpExchange ex, int code, Object body) throws IOException {
        send(ex, code, "application/json", gson.toJson(body));
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
        System.out.println(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " " + code);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
