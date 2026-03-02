package model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import score.Weights;

public class SearchQuery {
    private final double userLatitude;
    private final double userLongitude;
    private final double radiusKm;
    private final double budget;
    private final Set<String> preferredCuisines;
    private final DietaryPreference dietaryPreference;
    private final Weights weights;
    private final int topK;

    public SearchQuery(double userLatitude,
                       double userLongitude,
                       double radiusKm,
                       double budget,
                       Set<String> preferredCuisines,
                       DietaryPreference dietaryPreference,
                       Weights weights,
                       int topK) {
        this.userLatitude = userLatitude;
        this.userLongitude = userLongitude;
        this.radiusKm = radiusKm;
        this.budget = budget;
        this.preferredCuisines = new HashSet<>(preferredCuisines);
        this.dietaryPreference = dietaryPreference;
        this.weights = weights;
        this.topK = topK;
    }

    public double getUserLatitude() { return userLatitude; }
    public double getUserLongitude() { return userLongitude; }
    public double getRadiusKm() { return radiusKm; }
    public double getBudget() { return budget; }
    public Set<String> getPreferredCuisines() { return Collections.unmodifiableSet(preferredCuisines); }
    public DietaryPreference getDietaryPreference() { return dietaryPreference; }
    public Weights getWeights() { return weights; }
    public int getTopK() { return topK; }
}
