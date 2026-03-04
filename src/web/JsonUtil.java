package web;

import model.Cafe;
import model.CafeInsights;
import model.Recommendation;
import service.InsightsService;
import service.LiveStatus;
import service.LiveStatusService;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class JsonUtil {
    private JsonUtil() {}

    public static String recommendationsJson(List<Recommendation> recs,
                                             InsightsService insightsService,
                                             LiveStatusService liveStatusService,
                                             String source) {
        String items = recs.stream()
                .map(r -> recommendationJson(r, insightsService.forCafe(r.getCafe()), liveStatusService.getStatus(r.getCafe().getId())))
                .collect(Collectors.joining(","));
        return "{\"count\":" + recs.size() + ",\"source\":\"" + esc(source) + "\",\"results\":[" + items + "]}";
    }

    public static String liveStatusJson(String cafeId, LiveStatus status) {
        return "{" +
                "\"cafeId\":\"" + esc(cafeId) + "\"," +
                "\"easySeatVotes\":" + status.getEasySeatVotes() + "," +
                "\"standingVotes\":" + status.getStandingVotes() + "," +
                "\"tableShareAvailable\":" + status.isTableShareAvailable() +
                "}";
    }

    public static String errorJson(String message) {
        return "{\"error\":\"" + esc(message) + "\"}";
    }

    private static String recommendationJson(Recommendation r, CafeInsights i, LiveStatus live) {
        Cafe c = r.getCafe();
        String cuisines = jsonArray(c.getCuisines());
        String tags = jsonArray(i.getVibeTags());
        String milks = jsonArray(i.getAltMilks());
        String menuItems = jsonArray(i.getMenuItems());
        double matchPercent = Math.max(0.0, (1.0 - r.getScore()) * 100.0);

        return "{" +
                "\"id\":\"" + esc(c.getId()) + "\"," +
                "\"name\":\"" + esc(c.getName()) + "\"," +
                "\"latitude\":" + fmt(c.getLatitude()) + "," +
                "\"longitude\":" + fmt(c.getLongitude()) + "," +
                "\"distanceKm\":" + fmt(r.getDistanceKm()) + "," +
                "\"avgPrice\":" + fmt(c.getAvgPrice()) + "," +
                "\"rating\":" + fmt(c.getRating()) + "," +
                "\"cuisines\":" + cuisines + "," +
                "\"cuisineMatch\":" + fmt(r.getCuisineMatchRatio() * 100.0) + "," +
                "\"score\":" + fmt(r.getScore()) + "," +
                "\"displayMatch\":" + fmt(matchPercent) + "," +
                "\"address\":\"" + esc(c.getAddress()) + "\"," +
                "\"contact\":\"" + esc(c.getContact()) + "\"," +
                "\"operatingHours\":\"" + esc(c.getOperatingHours()) + "\"," +
                "\"vibeTags\":" + tags + "," +
                "\"workability\":{" +
                    "\"wifi\":" + i.getWifiScore() + "," +
                    "\"outlets\":" + i.getOutletScore() + "," +
                    "\"chairs\":" + i.getChairScore() + "," +
                    "\"overall\":" + i.getWorkabilityScore() +
                "}," +
                "\"acousticProfile\":\"" + esc(i.getAcousticProfile()) + "\"," +
                "\"altMilks\":" + milks + "," +
                "\"roastery\":\"" + esc(i.getRoastery()) + "\"," +
                "\"sunlightLabel\":\"" + esc(i.getSunlightLabel()) + "\"," +
                "\"independent\":" + i.isIndependent() + "," +
                "\"menuItems\":" + menuItems + "," +
                "\"bikeRack\":" + i.hasBikeRack() + "," +
                "\"parkingScore\":" + i.getParkingScore() + "," +
                "\"walkabilityScore\":" + i.getWalkabilityScore() + "," +
                "\"liveStatus\":{" +
                    "\"easySeatVotes\":" + live.getEasySeatVotes() + "," +
                    "\"standingVotes\":" + live.getStandingVotes() + "," +
                    "\"tableShareAvailable\":" + live.isTableShareAvailable() +
                "}" +
                "}";
    }

    private static String jsonArray(Set<String> values) {
        String body = values.stream().sorted().map(v -> "\"" + esc(v) + "\"").collect(Collectors.joining(","));
        return "[" + body + "]";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.6f", v);
    }
}
