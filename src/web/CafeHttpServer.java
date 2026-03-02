package web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import data.DataLoader;
import data.DataValidator;
import data.GlobalStats;
import model.Cafe;
import model.DietaryPreference;
import model.Recommendation;
import model.SearchQuery;
import score.Weights;
import service.RecommendationService;
import spatial.KDTree;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public class CafeHttpServer {
    private final int port;
    private final RecommendationService recommendationService;

    public CafeHttpServer(int port, RecommendationService recommendationService) {
        this.port = port;
        this.recommendationService = recommendationService;
    }

    public static CafeHttpServer buildDefault(int port, String csvPath) throws IOException {
        DataLoader loader = new DataLoader();
        List<Cafe> cafes = loader.loadFromCsv(csvPath);
        DataValidator.validate(cafes);
        GlobalStats stats = loader.computeGlobalStats(cafes);

        KDTree kdTree = new KDTree(cafes);
        RecommendationService service = new RecommendationService(cafes, kdTree, stats);
        return new CafeHttpServer(port, service);
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/recommend", new RecommendHandler(recommendationService));
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("Cafe Recommendation App running at http://localhost:" + port);
        System.out.println("Health endpoint: http://localhost:" + port + "/api/health");
    }

    private static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, JsonUtil.errorJson("Method not allowed"));
                return;
            }
            writeJson(ex, 200, "{\"status\":\"ok\"}");
        }
    }

    private static class RecommendHandler implements HttpHandler {
        private final RecommendationService service;

        RecommendHandler(RecommendationService service) {
            this.service = service;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, JsonUtil.errorJson("Use GET with query parameters"));
                return;
            }

            try {
                Map<String, String> q = RequestParsers.parseQuery(ex.getRequestURI().getRawQuery());
                double lat = Double.parseDouble(required(q, "lat"));
                double lon = Double.parseDouble(required(q, "lon"));
                double radius = Double.parseDouble(q.getOrDefault("radius", "5"));
                double budget = Double.parseDouble(q.getOrDefault("budget", "500"));
                int k = Integer.parseInt(q.getOrDefault("k", "10"));

                Set<String> cuisines = RequestParsers.parseCuisines(q.getOrDefault("cuisines", ""));
                DietaryPreference diet = RequestParsers.parseDiet(q.getOrDefault("diet", "ANY"));
                Weights weights = RequestParsers.parseWeights(q);

                SearchQuery query = new SearchQuery(lat, lon, radius, budget, cuisines, diet, weights, k);
                List<Recommendation> results = service.recommend(query);
                writeJson(ex, 200, JsonUtil.recommendationsJson(results));
            } catch (Exception err) {
                writeJson(ex, 400, JsonUtil.errorJson(err.getMessage()));
            }
        }

        private String required(Map<String, String> q, String key) {
            String v = q.get(key);
            if (v == null || v.isBlank()) {
                throw new IllegalArgumentException("Missing required parameter: " + key);
            }
            return v;
        }
    }

    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }

            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            Path file = Path.of("web", path.substring(1));
            if (!Files.exists(file) || Files.isDirectory(file)) {
                ex.sendResponseHeaders(404, -1);
                return;
            }

            byte[] data = Files.readAllBytes(file);
            ex.getResponseHeaders().set("Content-Type", contentType(file.toString()));
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(data);
            }
        }

        private String contentType(String path) {
            if (path.endsWith(".html")) {
                return "text/html; charset=UTF-8";
            }
            if (path.endsWith(".css")) {
                return "text/css; charset=UTF-8";
            }
            if (path.endsWith(".js")) {
                return "application/javascript; charset=UTF-8";
            }
            return "application/octet-stream";
        }
    }

    private static void writeJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
