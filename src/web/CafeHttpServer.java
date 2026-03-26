package web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import db.DatabaseManager;
import db.DatabaseStatus;
import db.AuthRepository;
import db.OnboardingRepository;
import db.SQLiteAuthRepository;
import db.SQLiteOnboardingRepository;
import data.DataLoader;
import data.DataValidator;
import data.GlobalStats;
import model.AmbiencePreference;
import model.AdminOverview;
import model.AppUser;
import model.AuthSession;
import model.Cafe;
import model.DietaryPreference;
import model.OnboardingProfile;
import model.Recommendation;
import model.SearchQuery;
import model.SocialPreference;
import model.StoredVisitContext;
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
import java.util.Optional;
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
    private final OnboardingRepository onboardingRepository;
    private final AuthRepository authRepository;

    public CafeHttpServer(int port,
                          String csvPath,
                          String xlsxPath,
                          Map<String, RecommendationService> recommendationServices,
                          Map<String, InsightsService> insightsServices,
                          LiveStatusService liveStatusService,
                          OnboardingRepository onboardingRepository,
                          AuthRepository authRepository) {
        this.port = port;
        this.csvPath = csvPath;
        this.xlsxPath = xlsxPath;
        this.recommendationServices = recommendationServices;
        this.insightsServices = insightsServices;
        this.liveStatusService = liveStatusService;
        this.onboardingRepository = onboardingRepository;
        this.authRepository = authRepository;
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
        return new CafeHttpServer(port, csvPath, xlsxPath, recommendationServices, insightsServices, liveStatusService, new SQLiteOnboardingRepository(), new SQLiteAuthRepository());
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
        return new CafeHttpServer(port, dataPath, dataPath, recommendationServices, insightsServices, liveStatusService, new SQLiteOnboardingRepository(), new SQLiteAuthRepository());
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/recommend", new RecommendHandler(recommendationServices, insightsServices, liveStatusService, onboardingRepository, authRepository));
        server.createContext("/api/onboarding/status", new OnboardingStatusHandler(onboardingRepository));
        server.createContext("/api/onboarding/profile", new OnboardingProfileHandler(onboardingRepository));
        server.createContext("/api/onboarding/context", new VisitContextHandler(onboardingRepository));
        server.createContext("/api/auth/register", new RegisterHandler(authRepository));
        server.createContext("/api/auth/login", new LoginHandler(authRepository));
        server.createContext("/api/auth/logout", new LogoutHandler(authRepository));
        server.createContext("/api/auth/me", new MeHandler(authRepository));
        server.createContext("/api/admin/overview", new AdminOverviewHandler(authRepository));
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
        DatabaseStatus databaseStatus = DatabaseManager.getStatus();
        System.out.println("SQLite onboarding storage: " + (databaseStatus.isEnabled() ? "enabled" : "disabled"));
    }

    private static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, JsonUtil.errorJson("Method not allowed"));
                return;
            }
            DatabaseStatus databaseStatus = DatabaseManager.getStatus();
            String health = "{"
                    + "\"status\":\"ok\","
                    + "\"defaultSource\":\"csv\","
                    + "\"availableSources\":[\"csv\",\"xlsx\"],"
                    + "\"databaseEnabled\":" + databaseStatus.isEnabled() + ","
                    + "\"databaseUrl\":\"" + escapeJson(databaseStatus.getJdbcUrl()) + "\","
                    + "\"databaseMessage\":\"" + escapeJson(databaseStatus.getMessage()) + "\""
                    + "}";
            writeJson(ex, 200, health);
        }
    }

    private static class RecommendHandler implements HttpHandler {
        private final Map<String, RecommendationService> services;
        private final Map<String, InsightsService> insightsServices;
        private final LiveStatusService liveStatusService;
        private final OnboardingRepository onboardingRepository;
        private final AuthRepository authRepository;

        RecommendHandler(Map<String, RecommendationService> services,
                         Map<String, InsightsService> insightsServices,
                         LiveStatusService liveStatusService,
                         OnboardingRepository onboardingRepository,
                         AuthRepository authRepository) {
            this.services = services;
            this.insightsServices = insightsServices;
            this.liveStatusService = liveStatusService;
            this.onboardingRepository = onboardingRepository;
            this.authRepository = authRepository;
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
                UserProfile requestUserProfile = RequestParsers.parseUserProfile(q, diet);
                VisitContext requestVisitContext = RequestParsers.parseVisitContext(q, (int) Math.max(1, Math.round(radius)));
                String source = RequestParsers.parseSource(q.getOrDefault("source", SOURCE_CSV));
                String sessionToken = q.getOrDefault("sessionToken", "").trim();
                String locationSource = q.getOrDefault("locationSource", "unknown").trim();
                String userKey = q.getOrDefault("userKey", "").trim();
                Optional<AppUser> sessionUser = resolveSessionUser(sessionToken);
                if (userKey.isBlank() && sessionUser.isPresent()) {
                    userKey = sessionUser.get().getUserKey();
                }
                RecommendationService service = services.get(source);
                InsightsService insightsService = insightsServices.get(source);
                ex.getResponseHeaders().set("X-Data-Source", source);

                OnboardingProfile storedOnboarding = loadStoredOnboarding(userKey);
                UserProfile userProfile = mergeUserProfile(storedOnboarding, requestUserProfile);
                VisitContext visitContext = mergeVisitContext(storedOnboarding, requestVisitContext);

                SearchQuery query = new SearchQuery(lat, lon, radius, budget, cuisines, diet, weights, k,
                        indieOnly, menuQuery, vibeTags, acoustic, userProfile, visitContext);
                List<Recommendation> results = service.recommend(query);
                if (sessionToken != null && !sessionToken.isBlank()) {
                    updateSessionLocation(sessionToken, lat, lon, locationSource);
                }
                persistRecommendationTrace(userKey, storedOnboarding, source, lat, lon, radius, budget, k, results);
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

        private OnboardingProfile loadStoredOnboarding(String userKey) {
            DatabaseStatus databaseStatus = DatabaseManager.getStatus();
            if (!databaseStatus.isEnabled() || userKey.isBlank()) {
                return null;
            }
            return onboardingRepository.findOnboardingProfile(userKey).orElse(null);
        }

        private Optional<AppUser> resolveSessionUser(String sessionToken) {
            DatabaseStatus databaseStatus = DatabaseManager.getStatus();
            if (!databaseStatus.isEnabled() || sessionToken == null || sessionToken.isBlank()) {
                return Optional.empty();
            }
            return authRepository.findUserBySessionToken(sessionToken);
        }

        private void updateSessionLocation(String sessionToken, double lat, double lon, String locationSource) {
            try {
                authRepository.updateSessionLocation(sessionToken, lat, lon, locationSource);
            } catch (Exception ignored) {
                System.err.println("Warning: failed to update session location: " + ignored.getMessage());
            }
        }

        private void persistRecommendationTrace(String userKey,
                                                OnboardingProfile onboardingProfile,
                                                String source,
                                                double lat,
                                                double lon,
                                                double radius,
                                                double budget,
                                                int topK,
                                                List<Recommendation> results) {
            DatabaseStatus databaseStatus = DatabaseManager.getStatus();
            if (!databaseStatus.isEnabled() || userKey.isBlank()) {
                return;
            }

            AppUser appUser = onboardingProfile == null
                    ? onboardingRepository.createOrGetUser(userKey, "")
                    : onboardingProfile.getAppUser();
            Long visitContextId = null;
            StoredVisitContext activeContext = onboardingProfile == null ? null : onboardingProfile.getActiveVisitContext();
            if (activeContext != null) {
                visitContextId = activeContext.getId();
            }

            try {
                model.RecommendationHistoryEntry history = onboardingRepository.saveRecommendationHistory(
                        appUser.getId(),
                        visitContextId,
                        source,
                        lat,
                        lon,
                        radius,
                        budget,
                        topK,
                        results.size()
                );
                onboardingRepository.saveRecommendationExplanations(history.getId(), results);
            } catch (Exception ignored) {
                System.err.println("Warning: failed to persist recommendation history: " + ignored.getMessage());
            }
        }
    }

    private static class OnboardingStatusHandler implements HttpHandler {
        private final OnboardingRepository onboardingRepository;

        OnboardingStatusHandler(OnboardingRepository onboardingRepository) {
            this.onboardingRepository = onboardingRepository;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, JsonUtil.errorJson("Use GET"));
                return;
            }
            if (!ensureDatabaseReady(ex)) {
                return;
            }

            try {
                Map<String, String> q = RequestParsers.parseQuery(ex.getRequestURI().getRawQuery());
                String userKey = required(q, "userKey");
                java.util.Optional<AppUser> appUser = onboardingRepository.findUserByKey(userKey);
                boolean profilePresent = appUser.flatMap(user -> onboardingRepository.findOnboardingProfile(user.getUserKey()))
                        .map(profile -> profile.getStoredUserProfile() != null)
                        .orElse(false);
                writeJson(ex, 200, JsonUtil.onboardingStatusJson(true, appUser.orElse(null), profilePresent));
            } catch (Exception err) {
                writeJson(ex, 400, JsonUtil.errorJson(err.getMessage()));
            }
        }
    }

    private static class OnboardingProfileHandler implements HttpHandler {
        private final OnboardingRepository onboardingRepository;

        OnboardingProfileHandler(OnboardingRepository onboardingRepository) {
            this.onboardingRepository = onboardingRepository;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!ensureDatabaseReady(ex)) {
                return;
            }

            try {
                Map<String, String> q = RequestParsers.parseQuery(ex.getRequestURI().getRawQuery());
                if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                    String userKey = required(q, "userKey");
                    java.util.Optional<OnboardingProfile> profile = onboardingRepository.findOnboardingProfile(userKey);
                    if (profile.isEmpty()) {
                        writeJson(ex, 404, JsonUtil.errorJson("Onboarding profile not found."));
                        return;
                    }
                    writeJson(ex, 200, JsonUtil.onboardingProfileJson(profile.get()));
                    return;
                }

                if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    writeJson(ex, 405, JsonUtil.errorJson("Use GET/POST"));
                    return;
                }

                String userKey = required(q, "userKey");
                DietaryPreference fallbackDiet = RequestParsers.parseDiet(q.getOrDefault("onboardingDiet", q.getOrDefault("diet", "ANY")));
                UserProfile userProfile = RequestParsers.parseUserProfile(q, fallbackDiet);
                AppUser appUser = onboardingRepository.createOrGetUser(userKey, userProfile.getName());
                onboardingRepository.saveUserProfile(appUser.getId(), userProfile);
                onboardingRepository.saveSocialPreference(appUser.getId(),
                        new SocialPreference(userProfile.getUsuallyVisitWith(), userProfile.getPreferredSeating()));
                onboardingRepository.saveAmbiencePreference(appUser.getId(),
                        new AmbiencePreference(userProfile.getMusicPreference(), userProfile.getLightingPreference()));

                OnboardingProfile profile = onboardingRepository.findOnboardingProfile(userKey)
                        .orElseThrow(() -> new IllegalStateException("Failed to reload onboarding profile after save."));
                writeJson(ex, 200, JsonUtil.onboardingProfileJson(profile));
            } catch (Exception err) {
                writeJson(ex, 400, JsonUtil.errorJson(err.getMessage()));
            }
        }
    }

    private static class VisitContextHandler implements HttpHandler {
        private final OnboardingRepository onboardingRepository;

        VisitContextHandler(OnboardingRepository onboardingRepository) {
            this.onboardingRepository = onboardingRepository;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!ensureDatabaseReady(ex)) {
                return;
            }

            try {
                Map<String, String> q = RequestParsers.parseQuery(ex.getRequestURI().getRawQuery());
                String userKey = required(q, "userKey");
                AppUser appUser = onboardingRepository.createOrGetUser(userKey, q.getOrDefault("userName", ""));

                if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                    java.util.Optional<model.StoredVisitContext> visitContext = onboardingRepository.findActiveVisitContext(appUser.getId());
                    if (visitContext.isEmpty()) {
                        writeJson(ex, 404, JsonUtil.errorJson("Active visit context not found."));
                        return;
                    }
                    writeJson(ex, 200, JsonUtil.visitContextJson(visitContext.get()));
                    return;
                }

                if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    writeJson(ex, 405, JsonUtil.errorJson("Use GET/POST"));
                    return;
                }

                int fallbackDistanceKm = RequestParsers.parseInt(q, "preferredDistanceKm", 5);
                VisitContext visitContext = RequestParsers.parseVisitContext(q, fallbackDistanceKm);
                model.StoredVisitContext stored = onboardingRepository.saveVisitContext(appUser.getId(), visitContext, true);
                writeJson(ex, 200, JsonUtil.visitContextJson(stored));
            } catch (Exception err) {
                writeJson(ex, 400, JsonUtil.errorJson(err.getMessage()));
            }
        }
    }

    private static class RegisterHandler implements HttpHandler {
        private final AuthRepository authRepository;

        RegisterHandler(AuthRepository authRepository) {
            this.authRepository = authRepository;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!ensureDatabaseReady(ex)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, JsonUtil.errorJson("Use POST"));
                return;
            }
            try {
                Map<String, String> q = RequestParsers.parseQuery(ex.getRequestURI().getRawQuery());
                AppUser user = authRepository.registerUser(required(q, "name"), required(q, "email"), required(q, "password"));
                AuthSession session = authRepository.login(required(q, "email"), required(q, "password"));
                writeJson(ex, 200, JsonUtil.authPayloadJson(user, session));
            } catch (Exception err) {
                writeJson(ex, 400, JsonUtil.errorJson(err.getMessage()));
            }
        }
    }

    private static class LoginHandler implements HttpHandler {
        private final AuthRepository authRepository;

        LoginHandler(AuthRepository authRepository) {
            this.authRepository = authRepository;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!ensureDatabaseReady(ex)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, JsonUtil.errorJson("Use POST"));
                return;
            }
            try {
                Map<String, String> q = RequestParsers.parseQuery(ex.getRequestURI().getRawQuery());
                AuthSession session = authRepository.login(required(q, "email"), required(q, "password"));
                AppUser user = authRepository.findUserBySessionToken(session.getSessionToken())
                        .orElseThrow(() -> new IllegalStateException("Unable to resolve logged-in user."));
                writeJson(ex, 200, JsonUtil.authPayloadJson(user, session));
            } catch (Exception err) {
                writeJson(ex, 401, JsonUtil.errorJson(err.getMessage()));
            }
        }
    }

    private static class LogoutHandler implements HttpHandler {
        private final AuthRepository authRepository;

        LogoutHandler(AuthRepository authRepository) {
            this.authRepository = authRepository;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!ensureDatabaseReady(ex)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, JsonUtil.errorJson("Use POST"));
                return;
            }
            try {
                Map<String, String> q = RequestParsers.parseQuery(ex.getRequestURI().getRawQuery());
                authRepository.logout(required(q, "sessionToken"));
                writeJson(ex, 200, "{\"ok\":true}");
            } catch (Exception err) {
                writeJson(ex, 400, JsonUtil.errorJson(err.getMessage()));
            }
        }
    }

    private static class MeHandler implements HttpHandler {
        private final AuthRepository authRepository;

        MeHandler(AuthRepository authRepository) {
            this.authRepository = authRepository;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!ensureDatabaseReady(ex)) {
                return;
            }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, JsonUtil.errorJson("Use GET"));
                return;
            }
            try {
                Map<String, String> q = RequestParsers.parseQuery(ex.getRequestURI().getRawQuery());
                String sessionToken = required(q, "sessionToken");
                AppUser user = authRepository.findUserBySessionToken(sessionToken)
                        .orElseThrow(() -> new IllegalArgumentException("Session not found."));
                AuthSession session = authRepository.findActiveSession(sessionToken)
                        .orElseThrow(() -> new IllegalArgumentException("Session not found."));
                writeJson(ex, 200, JsonUtil.authPayloadJson(user, session));
            } catch (Exception err) {
                writeJson(ex, 401, JsonUtil.errorJson(err.getMessage()));
            }
        }
    }

    private static class AdminOverviewHandler implements HttpHandler {
        private final AuthRepository authRepository;

        AdminOverviewHandler(AuthRepository authRepository) {
            this.authRepository = authRepository;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!ensureDatabaseReady(ex)) {
                return;
            }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, JsonUtil.errorJson("Use GET"));
                return;
            }
            try {
                Map<String, String> q = RequestParsers.parseQuery(ex.getRequestURI().getRawQuery());
                AppUser user = authRepository.findUserBySessionToken(required(q, "sessionToken"))
                        .orElseThrow(() -> new IllegalArgumentException("Session not found."));
                if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
                    writeJson(ex, 403, JsonUtil.errorJson("Admin access required."));
                    return;
                }
                AdminOverview overview = authRepository.getAdminOverview();
                writeJson(ex, 200, JsonUtil.adminOverviewJson(overview));
            } catch (Exception err) {
                writeJson(ex, 401, JsonUtil.errorJson(err.getMessage()));
            }
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
                path = "/landing.html";
            }

            Path file;
            if (path.startsWith("/images/")) {
                file = Path.of(path.substring(1));
            } else {
                file = Path.of("web", path.substring(1));
            }
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
            if (path.endsWith(".png")) {
                return "image/png";
            }
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                return "image/jpeg";
            }
            if (path.endsWith(".svg")) {
                return "image/svg+xml";
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

    private static boolean ensureDatabaseReady(HttpExchange ex) throws IOException {
        DatabaseStatus databaseStatus = DatabaseManager.getStatus();
        if (databaseStatus.isEnabled()) {
            return true;
        }
        writeJson(ex, 503, JsonUtil.errorJson(databaseStatus.getMessage()));
        return false;
    }

    private static String required(Map<String, String> q, String key) {
        String v = q.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return v;
    }

    private static UserProfile mergeUserProfile(OnboardingProfile stored, UserProfile request) {
        UserProfile base = stored == null ? UserProfile.empty() : stored.toUserProfile();
        if (request == null || !request.isProvided()) {
            return base;
        }

        return new UserProfile(
                choose(request.getName(), base.getName()),
                choose(request.getAgeGroup(), base.getAgeGroup()),
                choose(request.getOccupation(), base.getOccupation()),
                choose(request.getDefaultBudgetRange(), base.getDefaultBudgetRange()),
                choose(request.getPreferredCafeType(), base.getPreferredCafeType()),
                request.getPreferredDistanceKm() > 0 ? request.getPreferredDistanceKm() : base.getPreferredDistanceKm(),
                request.getDietaryPreference() != null && request.getDietaryPreference() != DietaryPreference.ANY
                        ? request.getDietaryPreference()
                        : base.getDietaryPreference(),
                choose(request.getUsuallyVisitWith(), base.getUsuallyVisitWith()),
                choose(request.getPreferredSeating(), base.getPreferredSeating()),
                choose(request.getMusicPreference(), base.getMusicPreference()),
                choose(request.getLightingPreference(), base.getLightingPreference())
        );
    }

    private static VisitContext mergeVisitContext(OnboardingProfile stored, VisitContext request) {
        VisitContext base = stored == null ? VisitContext.empty() : stored.toActiveVisitContext();
        if (request == null || !request.isProvided()) {
            return base;
        }

        return new VisitContext(
                choose(request.getPurposeOfVisit(), base.getPurposeOfVisit()),
                choose(request.getCurrentBudgetRange(), base.getCurrentBudgetRange()),
                request.getTravelDistanceKm() > 0 ? request.getTravelDistanceKm() : base.getTravelDistanceKm(),
                choose(request.getTimeOfVisit(), base.getTimeOfVisit()),
                choose(request.getCrowdTolerance(), base.getCrowdTolerance())
        );
    }

    private static String choose(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : (fallback == null ? "" : fallback);
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
