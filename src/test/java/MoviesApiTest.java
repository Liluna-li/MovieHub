import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.MoviesServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final int PORT = 8080;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static MoviesServer server;
    private static HttpClient client;

    @BeforeAll
    static void setUp() throws Exception {
        server = new MoviesServer(PORT);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterAll
    static void tearDown() {
        server.stop();
    }

    @BeforeEach
    void cleanStorage() {
        server.reset();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder(URI.create(BASE_URL + path));
    }

    private HttpResponse<String> post(String path, String json, String contentType) throws Exception {
        HttpRequest.Builder b = requestBuilder(path)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        return send(b.build());
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = send(requestBuilder("/movies").GET().build());

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void getMovies_whenExisting_returnsList() throws Exception {
        HttpResponse<String> created = post("/movies",
                "{\"title\":\"Фильм 1\",\"year\":2000}", "application/json");
        assertEquals(201, created.statusCode());

        HttpResponse<String> resp = send(requestBuilder("/movies").GET().build());
        assertEquals(200, resp.statusCode());
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertTrue(body.get("title").getAsString().contains("Фильм 1"), "Список должен содержать добавленный фильм");
        assertTrue(body.get("year").getAsString().contains("2000"));
    }

    @Test
    void postMovie_validData_returnsCreated() throws Exception {
        HttpResponse<String> resp = post("/movies",
                "{\"title\":\"Матрица\",\"year\":1999}", "application/json");

        assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201");
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType);
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertTrue(body.keySet().contains("id"), "Тело должно содержать id");
        assertTrue(body.get("title").getAsString().contains("Матрица"));
        assertTrue(body.get("year").getAsString().contains("1999"));
    }

    @Test
    void postMovie_emptyTitle_returns422() throws Exception {
        HttpResponse<String> resp = post("/movies",
                "{\"title\":\"\",\"year\":2000}", "application/json");

        assertEquals(422, resp.statusCode(), "Пустой title должен вернуть 422");
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertTrue(body.keySet().contains("error"), "Ответ должен содержать поле error");
        assertTrue(body.get("details").getAsString().contains("название не должно быть пустым"), "Ответ должен содержать детали");
    }

    @Test
    void postMovie_tooLongTitle_returns422() throws Exception {
        String longTitle = "a".repeat(101);
        HttpResponse<String> resp = post("/movies",
                "{\"title\":\"" + longTitle + "\",\"year\":2000}", "application/json");

        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals(422, resp.statusCode(), "Слишком длинный title должен вернуть 422");
        assertTrue(body.get("details").getAsString().contains("название должно быть не длиннее 100 символов"));
    }

    @Test
    void postMovie_invalidYear_returns422() throws Exception {
        HttpResponse<String> resp = post("/movies",
                "{\"title\":\"Фильм\",\"year\":1887}", "application/json");

        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals(422, resp.statusCode());
        assertTrue(body.get("details").getAsString().contains("год должен быть между 1888"));
    }

    @Test
    void postMovie_yearTooLarge_returns422() throws Exception {
        int maxYear = java.time.LocalDate.now().getYear() + 2;
        HttpResponse<String> resp = post("/movies",
                "{\"title\":\"Фильм\",\"year\":" + maxYear + "}", "application/json");

        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals(422, resp.statusCode());
        assertTrue(body.get("details").getAsString().contains("год должен быть между 1888"));
    }

    @Test
    void postMovie_wrongContentType_returns415() throws Exception {
        HttpResponse<String> resp = post("/movies",
                "{\"title\":\"Фильм\",\"year\":2000}", "text/plain");

        assertEquals(415, resp.statusCode(), "Некорректный Content-Type должен вернуть 415");
    }

    @Test
    void postMovie_invalidJson_returns400() throws Exception {
        HttpResponse<String> resp = post("/movies",
                "это не json", "application/json");

        assertEquals(400, resp.statusCode(), "Некорректный JSON должен вернуть 400");
    }

    @Test
    void getMovieById_existing_returnsMovie() throws Exception {
        HttpResponse<String> created = post("/movies",
                "{\"title\":\"Начало\",\"year\":2010}", "application/json");
        assertEquals(201, created.statusCode());

        // извлечь id из тела
        int id = extractId(created.body());

        HttpResponse<String> resp = send(requestBuilder("/movies/" + id).GET().build());
        assertEquals(200, resp.statusCode());
        assertEquals("application/json; charset=UTF-8",
                resp.headers().firstValue("Content-Type").orElse(""));
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertTrue(body.get("title").getAsString().contains("Начало"));
    }

    @Test
    void getMovieById_notFound_returns404() throws Exception {
        HttpResponse<String> resp = send(requestBuilder("/movies/999999").GET().build());
        assertEquals(404, resp.statusCode());
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertTrue(body.get("error").getAsString().contains("Фильм не найден"));
    }

    @Test
    void getMovieById_notNumber_returns400() throws Exception {
        HttpResponse<String> resp = send(requestBuilder("/movies/abc").GET().build());
        assertEquals(400, resp.statusCode());
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertTrue(body.get("error").getAsString().contains("Некорректный ID"));
    }

    @Test
    void deleteMovie_existing_returns204() throws Exception {
        HttpResponse<String> created = post("/movies",
                "{\"title\":\"Удалить\",\"year\":2000}", "application/json");

        int id = extractId(created.body());

        HttpResponse<String> resp = send(requestBuilder("/movies/" + id).DELETE().build());
        assertEquals(204, resp.statusCode());
    }

    @Test
    void deleteMovie_notFound_returns404() throws Exception {
        HttpResponse<String> resp = send(requestBuilder("/movies/999999").DELETE().build());
        assertEquals(404, resp.statusCode());
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertTrue(body.get("error").getAsString().contains("Фильм не найден"));
    }

    @Test
    void deleteMovie_notNumber_returns400() throws Exception {
        HttpResponse<String> resp = send(requestBuilder("/movies/abc").DELETE().build());
        assertEquals(400, resp.statusCode());
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertTrue(body.get("error").getAsString().contains("Некорректный ID"));
    }

    @Test
    void getMoviesByYear_returnsMovies() throws Exception {
        post("/movies", "{\"title\":\"Фильм А\",\"year\":1995}", "application/json");
        post("/movies", "{\"title\":\"Фильм Б\",\"year\":1995}", "application/json");
        post("/movies", "{\"title\":\"Фильм В\",\"year\":2010}", "application/json");

        HttpResponse<String> resp = send(requestBuilder("/movies?year=1995").GET().build());
        assertEquals(200, resp.statusCode());
        JsonArray body = JsonParser.parseString(resp.body()).getAsJsonArray();
        assertEquals(2, body.size());
        JsonObject movieA = body.get(0).getAsJsonObject();
        JsonObject movieB = body.get(1).getAsJsonObject();
        assertTrue(movieA.get("title").getAsString().contains("Фильм А"));
        assertTrue(movieB.get("title").getAsString().contains("Фильм Б"));
    }

    @Test
    void getMoviesByYear_noMatch_returnsEmptyList() throws Exception {
        HttpResponse<String> resp = send(requestBuilder("/movies?year=1900").GET().build());
        assertEquals(200, resp.statusCode());
        JsonArray body = JsonParser.parseString(resp.body()).getAsJsonArray();
        assertTrue(body.isEmpty());
    }

    @Test
    void getMoviesByYear_notNumber_returns400() throws Exception {
        HttpResponse<String> resp = send(requestBuilder("/movies?year=abc").GET().build());
        assertEquals(400, resp.statusCode());
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertTrue(body.get("error").getAsString().contains("Некорректный параметр запроса — 'year'"));
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        HttpResponse<String> resp = send(requestBuilder("/movies")
                .method("PUT", HttpRequest.BodyPublishers.noBody()).build());
        assertEquals(405, resp.statusCode());
    }

    private static int extractId(String body) {
        return JsonParser.parseString(body).getAsJsonObject()
                .get("id")
                .getAsInt();
    }
}