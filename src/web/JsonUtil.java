package web;

import model.Cafe;
import model.Recommendation;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class JsonUtil {
    private JsonUtil() {}

    public static String recommendationsJson(List<Recommendation> recs) {
        String items = recs.stream().map(JsonUtil::recommendationJson).collect(Collectors.joining(","));
        return "{\"count\":" + recs.size() + ",\"results\":[" + items + "]}";
    }

    public static String errorJson(String message) {
        return "{\"error\":\"" + esc(message) + "\"}";
    }

    private static String recommendationJson(Recommendation r) {
        Cafe c = r.getCafe();
        String cuisines = jsonArray(c.getCuisines());
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
                "\"operatingHours\":\"" + esc(c.getOperatingHours()) + "\"" +
                "}";
    }

    private static String jsonArray(Set<String> values) {
        String body = values.stream().map(v -> "\"" + esc(v) + "\"").collect(Collectors.joining(","));
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
