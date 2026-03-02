package filter;

import java.util.ArrayList;
import java.util.List;
import model.Cafe;
import model.DietaryPreference;

public class ConstraintFilter {
    public List<Cafe> byBudget(List<Cafe> cafes, double budget) {
        List<Cafe> out = new ArrayList<>();
        for (Cafe cafe : cafes) {
            if (cafe.getAvgPrice() <= budget) {
                out.add(cafe);
            }
        }
        return out;
    }

    public List<Cafe> byDiet(List<Cafe> cafes, DietaryPreference preference) {
        if (preference == DietaryPreference.ANY) {
            return cafes;
        }

        List<Cafe> out = new ArrayList<>();
        for (Cafe cafe : cafes) {
            boolean ok = switch (preference) {
                case VEG -> cafe.isVeg();
                case NON_VEG -> cafe.isNonVeg();
                case VEGAN -> cafe.isVegan();
                default -> true;
            };
            if (ok) {
                out.add(cafe);
            }
        }
        return out;
    }
}
