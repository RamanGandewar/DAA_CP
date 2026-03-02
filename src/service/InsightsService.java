package service;

import model.Cafe;
import model.CafeInsights;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class InsightsService {
    private final Map<String, CafeInsights> insightsByCafeId = new HashMap<>();

    private static final List<String> ACOUSTICS = List.of("Library Quiet", "Lo-fi Beats", "Active Chatter");
    private static final List<String> SUNLIGHT = List.of("Golden Hour Friendly", "Shade Friendly", "Balanced Light");
    private static final List<String> ROASTERIES = List.of("Blue Tokai", "KC Roasters", "Third Wave Roasters", "Subko", "Local Micro Roaster");

    public InsightsService(List<Cafe> cafes) {
        for (Cafe cafe : cafes) {
            insightsByCafeId.put(cafe.getId(), generate(cafe));
        }
    }

    public CafeInsights forCafe(Cafe cafe) {
        return insightsByCafeId.get(cafe.getId());
    }

    public boolean matchesMenu(Cafe cafe, String menuQuery) {
        if (menuQuery == null || menuQuery.isBlank()) {
            return true;
        }
        CafeInsights insights = forCafe(cafe);
        String q = menuQuery.toLowerCase(Locale.ROOT);
        for (String item : insights.getMenuItems()) {
            if (item.toLowerCase(Locale.ROOT).contains(q)) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesVibe(Cafe cafe, Set<String> vibeTags) {
        if (vibeTags == null || vibeTags.isEmpty()) {
            return true;
        }
        CafeInsights insights = forCafe(cafe);
        for (String tag : vibeTags) {
            if (insights.getVibeTags().contains(tag.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesAcoustic(Cafe cafe, String acoustic) {
        if (acoustic == null || acoustic.isBlank()) {
            return true;
        }
        CafeInsights insights = forCafe(cafe);
        return insights.getAcousticProfile().equalsIgnoreCase(acoustic);
    }

    private CafeInsights generate(Cafe cafe) {
        int seed = Math.abs((cafe.getId() + cafe.getName()).hashCode());

        Set<String> tags = new HashSet<>();
        String name = cafe.getName().toLowerCase(Locale.ROOT);
        if (name.contains("roast") || name.contains("brew")) {
            tags.add("#industrial");
        }
        if (name.contains("house") || name.contains("heritage")) {
            tags.add("#darkacademia");
        }
        if (tags.isEmpty()) {
            String[] pool = {"#darkacademia", "#industrial", "#sunlightheavy", "#plantseverywhere", "#minimalist", "#vintagecorners"};
            tags.add(pool[seed % pool.length]);
            tags.add(pool[(seed / 7) % pool.length]);
        }

        int wifi = 5 + seed % 6;
        int outlets = 4 + (seed / 3) % 7;
        int chairs = 4 + (seed / 5) % 7;

        Set<String> milks = new HashSet<>();
        String[] milkPool = {"oat", "almond", "soy"};
        milks.add(milkPool[seed % milkPool.length]);
        milks.add(milkPool[(seed / 2) % milkPool.length]);

        boolean independent = !isChain(cafe.getName());

        Set<String> menu = new HashSet<>(baseMenu(cafe));
        menu.add("iced spanish latte");
        menu.add("almond croissant");

        String acoustic = ACOUSTICS.get(seed % ACOUSTICS.size());
        String sunlightLabel = SUNLIGHT.get((seed / 11) % SUNLIGHT.size());
        String roastery = ROASTERIES.get((seed / 13) % ROASTERIES.size());

        boolean bikeRack = (seed % 2 == 0);
        int parkingScore = 3 + (seed / 17) % 8;
        int walkability = 4 + (seed / 19) % 7;

        return new CafeInsights(tags, wifi, outlets, chairs, acoustic, milks, roastery, sunlightLabel,
                independent, menu, bikeRack, parkingScore, walkability);
    }

    private boolean isChain(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("starbucks") || n.contains("costa") || n.contains("third wave") || n.contains("barista") || n.contains("cafe coffee day") || n.contains("ccd");
    }

    private List<String> baseMenu(Cafe cafe) {
        List<String> items = new ArrayList<>();
        items.add("espresso");
        items.add("cappuccino");
        items.add("latte");
        items.add("cold brew");
        for (String c : cafe.getCuisines()) {
            if (c.contains("dessert")) {
                items.add("tiramisu");
                items.add("cheesecake");
            }
            if (c.contains("italian")) {
                items.add("affogato");
                items.add("bruschetta");
            }
            if (c.contains("sandwich") || c.contains("burger")) {
                items.add("grilled sandwich");
            }
        }
        return items;
    }
}
