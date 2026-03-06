package model;

import java.util.Locale;

public class UserProfile {
    private final String name;
    private final String ageGroup;
    private final String occupation;
    private final String defaultBudgetRange;
    private final String preferredCafeType;
    private final int preferredDistanceKm;
    private final DietaryPreference dietaryPreference;
    private final String usuallyVisitWith;
    private final String preferredSeating;
    private final String musicPreference;
    private final String lightingPreference;

    public UserProfile(String name,
                       String ageGroup,
                       String occupation,
                       String defaultBudgetRange,
                       String preferredCafeType,
                       int preferredDistanceKm,
                       DietaryPreference dietaryPreference,
                       String usuallyVisitWith,
                       String preferredSeating,
                       String musicPreference,
                       String lightingPreference) {
        this.name = safe(name);
        this.ageGroup = norm(ageGroup);
        this.occupation = safe(occupation);
        this.defaultBudgetRange = norm(defaultBudgetRange);
        this.preferredCafeType = norm(preferredCafeType);
        this.preferredDistanceKm = preferredDistanceKm;
        this.dietaryPreference = dietaryPreference == null ? DietaryPreference.ANY : dietaryPreference;
        this.usuallyVisitWith = norm(usuallyVisitWith);
        this.preferredSeating = norm(preferredSeating);
        this.musicPreference = norm(musicPreference);
        this.lightingPreference = norm(lightingPreference);
    }

    public static UserProfile empty() {
        return new UserProfile("", "", "", "", "", 5, DietaryPreference.ANY, "", "", "", "");
    }

    public boolean isProvided() {
        return !name.isBlank() || !preferredCafeType.isBlank() || !ageGroup.isBlank();
    }

    public String getName() { return name; }
    public String getAgeGroup() { return ageGroup; }
    public String getOccupation() { return occupation; }
    public String getDefaultBudgetRange() { return defaultBudgetRange; }
    public String getPreferredCafeType() { return preferredCafeType; }
    public int getPreferredDistanceKm() { return preferredDistanceKm; }
    public DietaryPreference getDietaryPreference() { return dietaryPreference; }
    public String getUsuallyVisitWith() { return usuallyVisitWith; }
    public String getPreferredSeating() { return preferredSeating; }
    public String getMusicPreference() { return musicPreference; }
    public String getLightingPreference() { return lightingPreference; }

    private static String norm(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
