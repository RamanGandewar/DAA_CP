package db;

import java.util.List;
import java.util.Optional;
import model.AmbiencePreference;
import model.AppUser;
import model.OnboardingProfile;
import model.Recommendation;
import model.RecommendationHistoryEntry;
import model.SocialPreference;
import model.StoredUserProfile;
import model.StoredVisitContext;
import model.UserProfile;
import model.VisitContext;

public interface OnboardingRepository {
    AppUser createOrGetUser(String userKey, String displayName);

    Optional<AppUser> findUserByKey(String userKey);

    StoredUserProfile saveUserProfile(long userId, UserProfile userProfile);

    SocialPreference saveSocialPreference(long userId, SocialPreference socialPreference);

    AmbiencePreference saveAmbiencePreference(long userId, AmbiencePreference ambiencePreference);

    StoredVisitContext saveVisitContext(long userId, VisitContext visitContext, boolean active);

    Optional<OnboardingProfile> findOnboardingProfile(String userKey);

    Optional<StoredVisitContext> findActiveVisitContext(long userId);

    RecommendationHistoryEntry saveRecommendationHistory(long userId,
                                                         Long visitContextId,
                                                         String source,
                                                         double latitude,
                                                         double longitude,
                                                         double radiusKm,
                                                         double budget,
                                                         int topK,
                                                         int resultCount);

    void saveRecommendationExplanations(long recommendationHistoryId, List<Recommendation> recommendations);
}
