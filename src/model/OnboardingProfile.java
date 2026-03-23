package model;

public class OnboardingProfile {
    private final AppUser appUser;
    private final StoredUserProfile storedUserProfile;
    private final SocialPreference socialPreference;
    private final AmbiencePreference ambiencePreference;
    private final StoredVisitContext activeVisitContext;

    public OnboardingProfile(AppUser appUser,
                             StoredUserProfile storedUserProfile,
                             SocialPreference socialPreference,
                             AmbiencePreference ambiencePreference,
                             StoredVisitContext activeVisitContext) {
        this.appUser = appUser;
        this.storedUserProfile = storedUserProfile;
        this.socialPreference = socialPreference == null ? SocialPreference.empty() : socialPreference;
        this.ambiencePreference = ambiencePreference == null ? AmbiencePreference.empty() : ambiencePreference;
        this.activeVisitContext = activeVisitContext;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public StoredUserProfile getStoredUserProfile() {
        return storedUserProfile;
    }

    public SocialPreference getSocialPreference() {
        return socialPreference;
    }

    public AmbiencePreference getAmbiencePreference() {
        return ambiencePreference;
    }

    public StoredVisitContext getActiveVisitContext() {
        return activeVisitContext;
    }

    public UserProfile toUserProfile() {
        if (storedUserProfile == null) {
            return UserProfile.empty();
        }
        UserProfile base = storedUserProfile.getProfile();
        return new UserProfile(
                base.getName(),
                base.getAgeGroup(),
                base.getOccupation(),
                base.getDefaultBudgetRange(),
                base.getPreferredCafeType(),
                base.getPreferredDistanceKm(),
                base.getDietaryPreference(),
                socialPreference.getUsuallyVisitWith(),
                socialPreference.getPreferredSeating(),
                ambiencePreference.getMusicPreference(),
                ambiencePreference.getLightingPreference()
        );
    }

    public VisitContext toActiveVisitContext() {
        return activeVisitContext == null ? VisitContext.empty() : activeVisitContext.getVisitContext();
    }
}
