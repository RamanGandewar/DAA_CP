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
    private final boolean independentOnly;
    private final String menuQuery;
    private final Set<String> vibeTags;
    private final String acousticProfile;

    public SearchQuery(double userLatitude,
                       double userLongitude,
                       double radiusKm,
                       double budget,
                       Set<String> preferredCuisines,
                       DietaryPreference dietaryPreference,
                       Weights weights,
                       int topK) {
        this(userLatitude, userLongitude, radiusKm, budget, preferredCuisines, dietaryPreference, weights, topK, false, "", Set.of(), "");
    }

    public SearchQuery(double userLatitude,
                       double userLongitude,
                       double radiusKm,
                       double budget,
                       Set<String> preferredCuisines,
                       DietaryPreference dietaryPreference,
                       Weights weights,
                       int topK,
                       boolean independentOnly,
                       String menuQuery,
                       Set<String> vibeTags,
                       String acousticProfile) {
        this.userLatitude = userLatitude;
        this.userLongitude = userLongitude;
        this.radiusKm = radiusKm;
        this.budget = budget;
        this.preferredCuisines = new HashSet<>(preferredCuisines);
        this.dietaryPreference = dietaryPreference;
        this.weights = weights;
        this.topK = topK;
        this.independentOnly = independentOnly;
        this.menuQuery = menuQuery == null ? "" : menuQuery.trim().toLowerCase();
        this.vibeTags = new HashSet<>(vibeTags);
        this.acousticProfile = acousticProfile == null ? "" : acousticProfile.trim().toLowerCase();
    }

    public double getUserLatitude() { return userLatitude; }
    public double getUserLongitude() { return userLongitude; }
    public double getRadiusKm() { return radiusKm; }
    public double getBudget() { return budget; }
    public Set<String> getPreferredCuisines() { return Collections.unmodifiableSet(preferredCuisines); }
    public DietaryPreference getDietaryPreference() { return dietaryPreference; }
    public Weights getWeights() { return weights; }
    public int getTopK() { return topK; }
    public boolean isIndependentOnly() { return independentOnly; }
    public String getMenuQuery() { return menuQuery; }
    public Set<String> getVibeTags() { return Collections.unmodifiableSet(vibeTags); }
    public String getAcousticProfile() { return acousticProfile; }
}
