package model;

public class StoredUserProfile {
    private final long userId;
    private final UserProfile profile;
    private final ProfileTag dominantProfileTag;
    private final String createdAt;
    private final String updatedAt;

    public StoredUserProfile(long userId,
                             UserProfile profile,
                             ProfileTag dominantProfileTag,
                             String createdAt,
                             String updatedAt) {
        this.userId = userId;
        this.profile = profile == null ? UserProfile.empty() : profile;
        this.dominantProfileTag = dominantProfileTag;
        this.createdAt = createdAt == null ? "" : createdAt;
        this.updatedAt = updatedAt == null ? "" : updatedAt;
    }

    public long getUserId() {
        return userId;
    }

    public UserProfile getProfile() {
        return profile;
    }

    public ProfileTag getDominantProfileTag() {
        return dominantProfileTag;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
