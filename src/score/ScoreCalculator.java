package score;

import java.util.Set;
import data.GlobalStats;
import model.Cafe;

public class ScoreCalculator {
    private final GlobalStats stats;

    public ScoreCalculator(GlobalStats stats) {
        this.stats = stats;
    }

    public double cuisineMismatchScore(Set<String> preferredCuisines, Cafe cafe) {
        if (preferredCuisines == null || preferredCuisines.isEmpty()) {
            return 0;
        }

        int matches = 0;
        for (String c : preferredCuisines) {
            if (cafe.getCuisines().contains(c.toLowerCase())) {
                matches++;
            }
        }

        double matchRatio = (double) matches / preferredCuisines.size();
        return 1.0 - matchRatio;
    }

    public double cuisineMatchRatio(Set<String> preferredCuisines, Cafe cafe) {
        if (preferredCuisines == null || preferredCuisines.isEmpty()) {
            return 1.0;
        }

        int matches = 0;
        for (String c : preferredCuisines) {
            if (cafe.getCuisines().contains(c.toLowerCase())) {
                matches++;
            }
        }
        return (double) matches / preferredCuisines.size();
    }

    public double computeScore(Cafe cafe,
                               double distanceKm,
                               double maxRadiusKm,
                               Set<String> preferredCuisines,
                               Weights w) {
        double normalizedDistance = maxRadiusKm == 0 ? 1 : distanceKm / maxRadiusKm;

        double denom = stats.getMaxPrice() - stats.getMinPrice();
        double normalizedPrice = denom <= 0 ? 0 : (cafe.getAvgPrice() - stats.getMinPrice()) / denom;

        double ratingPenalty = (5.0 - cafe.getRating()) / 4.0;
        if (ratingPenalty < 0) {
            ratingPenalty = 0;
        }
        if (ratingPenalty > 1) {
            ratingPenalty = 1;
        }

        double cuisineMismatch = cuisineMismatchScore(preferredCuisines, cafe);

        return w.getWDistance() * normalizedDistance
                + w.getWPrice() * normalizedPrice
                + w.getWRating() * ratingPenalty
                + w.getWCuisine() * cuisineMismatch;
    }
}
