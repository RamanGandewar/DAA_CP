package model;

public class UserSummary {
    private final long userId;
    private final String displayName;
    private final String email;
    private final String role;
    private final boolean active;
    private final boolean onboardingCompleted;
    private final int loginCount;
    private final String lastLoginAt;
    private final String createdAt;
    private final String preferredCafeType;
    private final String defaultBudgetRange;
    private final String dietaryPreference;
    private final Double lastLocationLat;
    private final Double lastLocationLon;
    private final String lastLocationSource;
    private final int totalSearches;

    public UserSummary(long userId,
                       String displayName,
                       String email,
                       String role,
                       boolean active,
                       boolean onboardingCompleted,
                       int loginCount,
                       String lastLoginAt,
                       String createdAt,
                       String preferredCafeType,
                       String defaultBudgetRange,
                       String dietaryPreference,
                       Double lastLocationLat,
                       Double lastLocationLon,
                       String lastLocationSource,
                       int totalSearches) {
        this.userId = userId;
        this.displayName = displayName == null ? "" : displayName;
        this.email = email == null ? "" : email;
        this.role = role == null ? "USER" : role;
        this.active = active;
        this.onboardingCompleted = onboardingCompleted;
        this.loginCount = loginCount;
        this.lastLoginAt = lastLoginAt == null ? "" : lastLoginAt;
        this.createdAt = createdAt == null ? "" : createdAt;
        this.preferredCafeType = preferredCafeType == null ? "" : preferredCafeType;
        this.defaultBudgetRange = defaultBudgetRange == null ? "" : defaultBudgetRange;
        this.dietaryPreference = dietaryPreference == null ? "" : dietaryPreference;
        this.lastLocationLat = lastLocationLat;
        this.lastLocationLon = lastLocationLon;
        this.lastLocationSource = lastLocationSource == null ? "unknown" : lastLocationSource;
        this.totalSearches = totalSearches;
    }

    public long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public int getLoginCount() { return loginCount; }
    public String getLastLoginAt() { return lastLoginAt; }
    public String getCreatedAt() { return createdAt; }
    public String getPreferredCafeType() { return preferredCafeType; }
    public String getDefaultBudgetRange() { return defaultBudgetRange; }
    public String getDietaryPreference() { return dietaryPreference; }
    public Double getLastLocationLat() { return lastLocationLat; }
    public Double getLastLocationLon() { return lastLocationLon; }
    public String getLastLocationSource() { return lastLocationSource; }
    public int getTotalSearches() { return totalSearches; }
}
