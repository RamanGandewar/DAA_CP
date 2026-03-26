package model;

public class AppUser {
    private final long id;
    private final String userKey;
    private final String displayName;
    private final String email;
    private final String role;
    private final boolean active;
    private final boolean onboardingCompleted;
    private final String lastLoginAt;
    private final int loginCount;
    private final String createdAt;
    private final String updatedAt;

    public AppUser(long id,
                   String userKey,
                   String displayName,
                   String email,
                   String role,
                   boolean active,
                   boolean onboardingCompleted,
                   String lastLoginAt,
                   int loginCount,
                   String createdAt,
                   String updatedAt) {
        this.id = id;
        this.userKey = userKey == null ? "" : userKey;
        this.displayName = displayName == null ? "" : displayName;
        this.email = email == null ? "" : email;
        this.role = role == null ? "USER" : role;
        this.active = active;
        this.onboardingCompleted = onboardingCompleted;
        this.lastLoginAt = lastLoginAt == null ? "" : lastLoginAt;
        this.loginCount = loginCount;
        this.createdAt = createdAt == null ? "" : createdAt;
        this.updatedAt = updatedAt == null ? "" : updatedAt;
    }

    public AppUser(long id,
                   String userKey,
                   String displayName,
                   boolean onboardingCompleted,
                   String createdAt,
                   String updatedAt) {
        this(id, userKey, displayName, "", "USER", true, onboardingCompleted, "", 0, createdAt, updatedAt);
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

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public String getLastLoginAt() {
        return lastLoginAt;
    }

    public int getLoginCount() {
        return loginCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
