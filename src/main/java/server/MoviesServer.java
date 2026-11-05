package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import model.Movie;
import storage.MovieStorage;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MoviesServer extends BaseHttpHandler {

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private MovieStorage storage = new MovieStorage();
    private final HttpServer server;

    public MoviesServer(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/movies", this::handle);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public void reset() {
        storage = new MovieStorage();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();

            if (!path.startsWith("/movies")) {
                sendError(exchange, 404, "Фильм не найден");
                return;
            }

            if (path.equals("/movies")) {
                switch (method) {
                    case "GET":
                        handleGetAll(exchange, query);
                        return;
                    case "POST":
                        handlePost(exchange);
                        return;
                    default:
                        sendError(exchange, 405, "Метод не поддерживается");
                }
            } else {
                String idPart = path.substring("/movies/".length());
                switch (method) {
                    case "GET":
                        handleGetById(exchange, idPart);
                        return;
                    case "DELETE":
                        handleDelete(exchange, idPart);
                        return;
                    default:
                        sendError(exchange, 405, "Метод не поддерживается");
                }
            }
        } catch (Exception e) {
            sendError(exchange, 500, "Внутренняя ошибка сервера");
        }
    }

    private void handleGetAll(HttpExchange exchange, String query) throws IOException {
        String yearParam = null;
        if (query != null) {
            for (String kv : query.split("&")) {
                int eq = kv.indexOf('=');
                if (eq > 0 && kv.substring(0, eq).equals("year")) {
                    yearParam = kv.substring(eq + 1);
                }
            }
        }

        if (yearParam != null) {
            Integer year = parseYear(yearParam);
            if (year == null) {
                sendError(exchange, 400, "Некорректный параметр запроса — 'year'");
                return;
            }
            sendJson(exchange, 200, storage.findByYear(year));
            return;
        }

        sendJson(exchange, 200, storage.findAll());
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendError(exchange, 415, "Некорректный Content-Type");
            return;
        }

        String body = readBody(exchange);

        String title;
        Integer year;
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            title = obj.has("title") && !obj.get("title").isJsonNull() ? obj.get("title").getAsString() : null;
            year = obj.has("year") && !obj.get("year").isJsonNull() ? obj.get("year").getAsInt() : null;
        } catch (Exception e) {
            sendError(exchange, 400, "Некорректный JSON");
            return;
        }

        if (title == null || year == null) {
            sendError(exchange, 400, "Некорректный JSON");
            return;
        }

        List<String> details = new ArrayList<>();
        if (title.isEmpty()) {
            details.add("название не должно быть пустым");
        } else if (title.length() > 100) {
            details.add("название должно быть не длиннее 100 символов");
        }
        int currentYear = LocalDate.now().getYear();
        if (year < 1888 || year > currentYear + 1) {
            details.add("год должен быть между 1888 и " + (currentYear + 1));
        }

        if (!details.isEmpty()) {
            sendValidationError(exchange, details);
            return;
        }

        Movie movie = storage.add(title, year);
        sendJson(exchange, 201, movie);
    }

    private void handleGetById(HttpExchange exchange, String idPart) throws IOException {
        Integer id = parseId(idPart);
        if (id == null) {
            sendError(exchange, 400, "Некорректный ID");
            return;
        }
        Movie movie = storage.findById(id);
        if (movie == null) {
            sendError(exchange, 404, "Фильм не найден");
            return;
        }
        sendJson(exchange, 200, movie);
    }

    private void handleDelete(HttpExchange exchange, String idPart) throws IOException {
        Integer id = parseId(idPart);
        if (id == null) {
            sendError(exchange, 400, "Некорректный ID");
            return;
        }
        if (!storage.remove(id)) {
            sendError(exchange, 404, "Фильм не найден");
            return;
        }
        sendNoContent(exchange);
    }

    private static Integer parseId(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseYear(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        sendJson(exchange, status, obj);
    }

    private void sendValidationError(HttpExchange exchange, List<String> details) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", "Ошибка валидации");
        obj.add("details", gson.toJsonTree(details));
        sendJson(exchange, 422, obj);
    }

    private void sendJson(HttpExchange exchange, int status, Object data) throws IOException {
        sendJson(exchange, status, gson.toJson(data));
    }
}