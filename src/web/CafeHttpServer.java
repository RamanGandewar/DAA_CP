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
import model.UserProfile;
import model.VisitContext;
import score.Weights;
import service.InsightsService;
import service.LiveStatus;
import service.LiveStatusService;
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
    private static final String SOURCE_CSV = "csv";
    private static final String SOURCE_XLSX = "xlsx";

    private final int port;
    private final String csvPath;
    private final String xlsxPath;
    private final Map<String, RecommendationService> recommendationServices;
    private final Map<String, InsightsService> insightsServices;
    private final LiveStatusService liveStatusService;

    public CafeHttpServer(int port,
                          String csvPath,
                          String xlsxPath,
                          Map<String, RecommendationService> recommendationServices,
                          Map<String, InsightsService> insightsServices,
                          LiveStatusService liveStatusService) {
        this.port = port;
        this.csvPath = csvPath;
        this.xlsxPath = xlsxPath;
        this.recommendationServices = recommendationServices;
        this.insightsServices = insightsServices;
        this.liveStatusService = liveStatusService;
    }

    public static CafeHttpServer buildDefault(int port) throws IOException {
        String csvPath = "data/cafes.csv";
        String geocodedPath = "data/micuppa cafe dataset.geocoded.xlsx";
        String xlsxPath = Files.exists(Path.of(geocodedPath))
                ? geocodedPath
                : "data/micuppa cafe dataset.xlsx";

        Map<String, RecommendationService> recommendationServices = new java.util.HashMap<>();
        Map<String, InsightsService> insightsServices = new java.util.HashMap<>();

        registerSource(recommendationServices, insightsServices, SOURCE_CSV, csvPath);
        registerSource(recommendationServices, insightsServices, SOURCE_XLSX, xlsxPath);

        LiveStatusService liveStatusService = new LiveStatusService();
        return new CafeHttpServer(port, csvPath, xlsxPath, recommendationServices, insightsServices, liveStatusService);
    }

    public static CafeHttpServer buildDefault(int port, String dataPath) throws IOException {
        DataLoader loader = new DataLoader();
        List<Cafe> cafes = loader.loadFromFile(dataPath);
        DataValidator.validate(cafes);
        GlobalStats stats = loader.computeGlobalStats(cafes);

        KDTree kdTree = new KDTree(cafes);
        InsightsService insightsService = new InsightsService(cafes);
        LiveStatusService liveStatusService = new LiveStatusService();
        RecommendationService service = new RecommendationService(cafes, kdTree, stats, insightsService);
        Map<String, RecommendationService> recommendationServices = Map.of(SOURCE_XLSX, service, SOURCE_CSV, service);
        Map<String, InsightsService> insightsServices = Map.of(SOURCE_XLSX, insightsService, SOURCE_CSV, insightsService);
        return new CafeHttpServer(port, dataPath, dataPath, recommendationServices, insightsServices, liveStatusService);
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/recommend", new RecommendHandler(recommendationServices, insightsServices, liveStatusService));
        server.createContext("/api/live/seat", new SeatStatusHandler(liveStatusService));
        server.createContext("/api/live/table-share", new TableShareHandler(liveStatusService));
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("Cafe Recommendation App running at http://localhost:" + port);
        System.out.println("Health endpoint: http://localhost:" + port + "/api/health");
        System.out.println("Dataset selected: csv (default). Change in UI to xlsx when needed.");
        System.out.println("CSV dataset path: " + csvPath);
        System.out.println("XLSX dataset path: " + xlsxPath);
    }

    private static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, JsonUtil.errorJson("Method not allowed"));
                return;
            }
            writeJson(ex, 200, "{\"status\":\"ok\",\"defaultSource\":\"csv\",\"availableSources\":[\"csv\",\"xlsx\"]}");
        }
    }

    private static class RecommendHandler implements HttpHandler {
        private final Map<String, RecommendationService> services;
        private final Map<String, InsightsService> insightsServices;
        private final LiveStatusService liveStatusService;

        RecommendHandler(Map<String, RecommendationService> services,
                         Map<String, InsightsService> insightsServices,
                         LiveStatusService liveStatusService) {
            this.services = services;
            this.insightsServices = insightsServices;
            this.liveStatusService = liveStatusService;
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
                int k = Integer.parseInt(q.getOrDefault("k", "20"));

                Set<String> cuisines = RequestParsers.parseCuisines(q.getOrDefault("cuisines", ""));
                Set<String> vibeTags = RequestParsers.parseTags(q.getOrDefault("vibes", ""));
                DietaryPreference diet = RequestParsers.parseDiet(q.getOrDefault("diet", "ANY"));
                Weights weights = RequestParsers.parseWeights(q);
                boolean indieOnly = RequestParsers.parseBoolean(q, "indieOnly", false);
                String menuQuery = q.getOrDefault("menuQuery", "");
                String acoustic = q.getOrDefault("acoustic", "");
                UserProfile userProfile = RequestParsers.parseUserProfile(q, diet);
                VisitContext visitContext = RequestParsers.parseVisitContext(q, (int) Math.max(1, Math.round(radius)));
                String source = RequestParsers.parseSource(q.getOrDefault("source", SOURCE_CSV));
                RecommendationService service = services.get(source);
                InsightsService insightsService = insightsServices.get(source);
                ex.getResponseHeaders().set("X-Data-Source", source);

                SearchQuery query = new SearchQuery(lat, lon, radius, budget, cuisines, diet, weights, k,
                        indieOnly, menuQuery, vibeTags, acoustic, userProfile, visitContext);
                List<Recommendation> results = service.recommend(query);
                writeJson(ex, 200, JsonUtil.recommendationsJson(results, insightsService, liveStatusService, source));
                System.out.println("Dataset selected: " + source + " (/api/recommend)");
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

    private static void registerSource(Map<String, RecommendationService> recommendationServices,
                                       Map<String, InsightsService> insightsServices,
                                       String key,
                                       String dataPath) throws IOException {
        DataLoader loader = new DataLoader();
        List<Cafe> cafes = loader.loadFromFile(dataPath);
        DataValidator.validate(cafes);
        GlobalStats stats = loader.computeGlobalStats(cafes);
        KDTree kdTree = new KDTree(cafes);
        InsightsService insightsService = new InsightsService(cafes);
        RecommendationService recommendationService = new RecommendationService(cafes, kdTree, stats, insightsService);
        recommendationServices.put(key, recommendationService);
        insightsServices.put(key, insightsService);
    }

    private static class SeatStatusHandler implements HttpHandler {
        private final LiveStatusService liveStatusService;

        SeatStatusHandler(LiveStatusService liveStatusService) {
            this.liveStatusService = liveStatusService;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                writePreflight(ex);
                return;
            }

            Map<String, String> q = RequestParsers.parseQuery(ex.getRequestURI().getRawQuery());
            String cafeId = q.getOrDefault("cafeId", "");
            if (cafeId.isBlank()) {
                writeJson(ex, 400, JsonUtil.errorJson("Missing cafeId"));
                return;
            }

            try {
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    String status = q.getOrDefault("status", "");
                    liveStatusService.voteSeatStatus(cafeId, status);
                } else if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                    writeJson(ex, 405, JsonUtil.errorJson("Use GET/POST"));
                    return;
                }

                LiveStatus status = liveStatusService.getStatus(cafeId);
                writeJson(ex, 200, JsonUtil.liveStatusJson(cafeId, status));
            } catch (Exception err) {
                writeJson(ex, 400, JsonUtil.errorJson(err.getMessage()));
            }
        }
    }

    private static class TableShareHandler implements HttpHandler {
        private final LiveStatusService liveStatusService;

        TableShareHandler(LiveStatusService liveStatusService) {
            this.liveStatusService = liveStatusService;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                writePreflight(ex);
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, JsonUtil.errorJson("Use POST"));
                return;
            }

            Map<String, String> q = RequestParsers.parseQuery(ex.getRequestURI().getRawQuery());
            String cafeId = q.getOrDefault("cafeId", "");
            if (cafeId.isBlank()) {
                writeJson(ex, 400, JsonUtil.errorJson("Missing cafeId"));
                return;
            }
            boolean available = RequestParsers.parseBoolean(q, "available", false);
            liveStatusService.setTableShare(cafeId, available);
            LiveStatus status = liveStatusService.getStatus(cafeId);
            writeJson(ex, 200, JsonUtil.liveStatusJson(cafeId, status));
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
            ex.getResponseHeaders().set("Cache-Control", "no-store");
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

    private static void writePreflight(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.sendResponseHeaders(204, -1);
    }

    private static void writeJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
