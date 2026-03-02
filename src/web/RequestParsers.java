package web;

import model.DietaryPreference;
import score.Weights;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RequestParsers {
    private RequestParsers() {}

    public static Map<String, String> parseQuery(String query) {
        Map<String, String> out = new HashMap<>();
        if (query == null || query.isBlank()) {
            return out;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key;
            String value;
            if (idx >= 0) {
                key = decode(pair.substring(0, idx));
                value = decode(pair.substring(idx + 1));
            } else {
                key = decode(pair);
                value = "";
            }
            out.put(key, value);
        }
        return out;
    }

    public static Set<String> parseCuisines(String raw) {
        Set<String> cuisines = new HashSet<>();
        if (raw == null || raw.isBlank()) {
            return cuisines;
        }
        for (String c : raw.split(",")) {
            String value = c.trim().toLowerCase();
            if (!value.isEmpty()) {
                cuisines.add(value);
            }
        }
        return cuisines;
    }

    public static Set<String> parseTags(String raw) {
        Set<String> tags = new HashSet<>();
        if (raw == null || raw.isBlank()) {
            return tags;
        }
        for (String c : raw.split(",")) {
            String value = c.trim().toLowerCase();
            if (!value.isEmpty()) {
                if (!value.startsWith("#")) {
                    value = "#" + value;
                }
                tags.add(value);
            }
        }
        return tags;
    }

    public static DietaryPreference parseDiet(String raw) {
        if (raw == null || raw.isBlank()) {
            return DietaryPreference.ANY;
        }
        return DietaryPreference.valueOf(raw.trim().toUpperCase());
    }

    public static Weights parseWeights(Map<String, String> q) {
        String w1 = q.getOrDefault("w1", "0.3");
        String w2 = q.getOrDefault("w2", "0.3");
        String w3 = q.getOrDefault("w3", "0.2");
        String w4 = q.getOrDefault("w4", "0.2");
        return new Weights(
                Double.parseDouble(w1),
                Double.parseDouble(w2),
                Double.parseDouble(w3),
                Double.parseDouble(w4)
        );
    }

    public static boolean parseBoolean(Map<String, String> q, String key, boolean defaultVal) {
        String raw = q.get(key);
        if (raw == null || raw.isBlank()) {
            return defaultVal;
        }
        return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw);
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
