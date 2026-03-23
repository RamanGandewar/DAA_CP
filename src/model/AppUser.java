package model;

public class AppUser {
    private final long id;
    private final String userKey;
    private final String displayName;
    private final boolean onboardingCompleted;
    private final String createdAt;
    private final String updatedAt;

    public AppUser(long id,
                   String userKey,
                   String displayName,
                   boolean onboardingCompleted,
                   String createdAt,
                   String updatedAt) {
        this.id = id;
        this.userKey = userKey == null ? "" : userKey;
        this.displayName = displayName == null ? "" : displayName;
        this.onboardingCompleted = onboardingCompleted;
        this.createdAt = createdAt == null ? "" : createdAt;
        this.updatedAt = updatedAt == null ? "" : updatedAt;
    }

    public long getId() {
        return id;
    }

    public String getUserKey() {
        return userKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
