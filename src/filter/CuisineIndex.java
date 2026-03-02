package filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.Cafe;

public class CuisineIndex {
    private final Map<String, List<Cafe>> cuisineToCafes = new HashMap<>();

    public CuisineIndex(List<Cafe> cafes) {
        for (Cafe cafe : cafes) {
            for (String cuisine : cafe.getCuisines()) {
                cuisineToCafes.computeIfAbsent(cuisine.toLowerCase(), k -> new ArrayList<>()).add(cafe);
            }
        }
    }

    public List<Cafe> getByCuisine(String cuisine) {
        return cuisineToCafes.getOrDefault(cuisine.toLowerCase(), List.of());
    }
}
