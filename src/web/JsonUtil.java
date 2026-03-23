package web;

import model.AmbiencePreference;
import model.AppUser;
import model.Cafe;
import model.CafeInsights;
import model.OnboardingProfile;
import model.Recommendation;
import model.SocialPreference;
import model.StoredUserProfile;
import model.StoredVisitContext;
import model.UserProfile;
import model.VisitContext;
import service.InsightsService;
import service.LiveStatus;
import service.LiveStatusService;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class JsonUtil {
    private JsonUtil() {}

    public static String recommendationsJson(List<Recommendation> recs,
                                             InsightsService insightsService,
                                             LiveStatusService liveStatusService,
                                             String source) {
        String items = recs.stream()
                .map(r -> recommendationJson(r, insightsService.forCafe(r.getCafe()), liveStatusService.getStatus(r.getCafe().getId())))
                .collect(Collectors.joining(","));
        return "{\"count\":" + recs.size() + ",\"source\":\"" + esc(source) + "\",\"results\":[" + items + "]}";
    }

    public static String liveStatusJson(String cafeId, LiveStatus status) {
        return "{" +
                "\"cafeId\":\"" + esc(cafeId) + "\"," +
                "\"easySeatVotes\":" + status.getEasySeatVotes() + "," +
                "\"standingVotes\":" + status.getStandingVotes() + "," +
                "\"tableShareAvailable\":" + status.isTableShareAvailable() +
                "}";
    }

    public static String errorJson(String message) {
        return "{\"error\":\"" + esc(message) + "\"}";
    }

    public static String onboardingStatusJson(boolean databaseEnabled, AppUser appUser, boolean profilePresent) {
        return "{"
                + "\"databaseEnabled\":" + databaseEnabled + ","
                + "\"userExists\":" + (appUser != null) + ","
                + "\"onboardingCompleted\":" + (appUser != null && appUser.isOnboardingCompleted()) + ","
                + "\"profilePresent\":" + profilePresent + ","
                + "\"user\":" + appUserJson(appUser)
                + "}";
    }

    public static String onboardingProfileJson(OnboardingProfile onboardingProfile) {
        if (onboardingProfile == null) {
            return "{\"profile\":null}";
        }
        return "{"
                + "\"user\":" + appUserJson(onboardingProfile.getAppUser()) + ","
                + "\"profile\":" + storedUserProfileJson(onboardingProfile.getStoredUserProfile()) + ","
                + "\"socialPreference\":" + socialPreferenceJson(onboardingProfile.getSocialPreference()) + ","
                + "\"ambiencePreference\":" + ambiencePreferenceJson(onboardingProfile.getAmbiencePreference()) + ","
                + "\"activeVisitContext\":" + storedVisitContextJson(onboardingProfile.getActiveVisitContext())
                + "}";
    }

    public static String visitContextJson(StoredVisitContext visitContext) {
        return "{"
                + "\"activeVisitContext\":" + storedVisitContextJson(visitContext)
                + "}";
    }

    private static String recommendationJson(Recommendation r, CafeInsights i, LiveStatus live) {
        Cafe c = r.getCafe();
        String cuisines = jsonArray(c.getCuisines());
        String tags = jsonArray(i.getVibeTags());
        String milks = jsonArray(i.getAltMilks());
        String menuItems = jsonArray(i.getMenuItems());
        double matchPercent = Math.max(0.0, (1.0 - r.getScore()) * 100.0);

        return "{" +
                "\"id\":\"" + esc(c.getId()) + "\"," +
                "\"name\":\"" + esc(c.getName()) + "\"," +
                "\"latitude\":" + fmt(c.getLatitude()) + "," +
                "\"longitude\":" + fmt(c.getLongitude()) + "," +
                "\"distanceKm\":" + fmt(r.getDistanceKm()) + "," +
                "\"avgPrice\":" + fmt(c.getAvgPrice()) + "," +
                "\"rating\":" + fmt(c.getRating()) + "," +
                "\"cuisines\":" + cuisines + "," +
                "\"cuisineMatch\":" + fmt(r.getCuisineMatchRatio() * 100.0) + "," +
                "\"score\":" + fmt(r.getScore()) + "," +
                "\"displayMatch\":" + fmt(matchPercent) + "," +
                "\"profileTag\":\"" + esc(r.getProfileTag() == null ? "" : r.getProfileTag().getLabel()) + "\"," +
                "\"explanation\":\"" + esc(r.getExplanation()) + "\"," +
                "\"address\":\"" + esc(c.getAddress()) + "\"," +
                "\"contact\":\"" + esc(c.getContact()) + "\"," +
                "\"operatingHours\":\"" + esc(c.getOperatingHours()) + "\"," +
                "\"vibeTags\":" + tags + "," +
                "\"workability\":{" +
                    "\"wifi\":" + i.getWifiScore() + "," +
                    "\"outlets\":" + i.getOutletScore() + "," +
                    "\"chairs\":" + i.getChairScore() + "," +
                    "\"overall\":" + i.getWorkabilityScore() +
                "}," +
                "\"acousticProfile\":\"" + esc(i.getAcousticProfile()) + "\"," +
                "\"altMilks\":" + milks + "," +
                "\"roastery\":\"" + esc(i.getRoastery()) + "\"," +
                "\"sunlightLabel\":\"" + esc(i.getSunlightLabel()) + "\"," +
                "\"independent\":" + i.isIndependent() + "," +
                "\"menuItems\":" + menuItems + "," +
                "\"bikeRack\":" + i.hasBikeRack() + "," +
                "\"parkingScore\":" + i.getParkingScore() + "," +
                "\"walkabilityScore\":" + i.getWalkabilityScore() + "," +
                "\"liveStatus\":{" +
                    "\"easySeatVotes\":" + live.getEasySeatVotes() + "," +
                    "\"standingVotes\":" + live.getStandingVotes() + "," +
                    "\"tableShareAvailable\":" + live.isTableShareAvailable() +
                "}" +
                "}";
    }

    private static String appUserJson(AppUser appUser) {
        if (appUser == null) {
            return "null";
        }
        return "{"
                + "\"id\":" + appUser.getId() + ","
                + "\"userKey\":\"" + esc(appUser.getUserKey()) + "\","
                + "\"displayName\":\"" + esc(appUser.getDisplayName()) + "\","
                + "\"onboardingCompleted\":" + appUser.isOnboardingCompleted() + ","
                + "\"createdAt\":\"" + esc(appUser.getCreatedAt()) + "\","
                + "\"updatedAt\":\"" + esc(appUser.getUpdatedAt()) + "\""
                + "}";
    }

    private static String storedUserProfileJson(StoredUserProfile storedUserProfile) {
        if (storedUserProfile == null) {
            return "null";
        }
        UserProfile profile = storedUserProfile.getProfile();
        return "{"
                + "\"userId\":" + storedUserProfile.getUserId() + ","
                + "\"name\":\"" + esc(profile.getName()) + "\","
                + "\"ageGroup\":\"" + esc(profile.getAgeGroup()) + "\","
                + "\"occupation\":\"" + esc(profile.getOccupation()) + "\","
                + "\"defaultBudgetRange\":\"" + esc(profile.getDefaultBudgetRange()) + "\","
                + "\"preferredCafeType\":\"" + esc(profile.getPreferredCafeType()) + "\","
                + "\"preferredDistanceKm\":" + profile.getPreferredDistanceKm() + ","
                + "\"dietaryPreference\":\"" + esc(profile.getDietaryPreference().name()) + "\","
                + "\"dominantProfileTag\":\"" + esc(storedUserProfile.getDominantProfileTag() == null ? "" : storedUserProfile.getDominantProfileTag().getLabel()) + "\","
                + "\"createdAt\":\"" + esc(storedUserProfile.getCreatedAt()) + "\","
                + "\"updatedAt\":\"" + esc(storedUserProfile.getUpdatedAt()) + "\""
                + "}";
    }

    private static String socialPreferenceJson(SocialPreference socialPreference) {
        if (socialPreference == null) {
            return "null";
        }
        return "{"
                + "\"usuallyVisitWith\":\"" + esc(socialPreference.getUsuallyVisitWith()) + "\","
                + "\"preferredSeating\":\"" + esc(socialPreference.getPreferredSeating()) + "\""
                + "}";
    }

    private static String ambiencePreferenceJson(AmbiencePreference ambiencePreference) {
        if (ambiencePreference == null) {
            return "null";
        }
        return "{"
                + "\"musicPreference\":\"" + esc(ambiencePreference.getMusicPreference()) + "\","
                + "\"lightingPreference\":\"" + esc(ambiencePreference.getLightingPreference()) + "\""
                + "}";
    }

    private static String storedVisitContextJson(StoredVisitContext storedVisitContext) {
        if (storedVisitContext == null) {
            return "null";
        }
        VisitContext visitContext = storedVisitContext.getVisitContext();
        return "{"
                + "\"id\":" + storedVisitContext.getId() + ","
                + "\"userId\":" + storedVisitContext.getUserId() + ","
                + "\"purposeOfVisit\":\"" + esc(visitContext.getPurposeOfVisit()) + "\","
                + "\"currentBudgetRange\":\"" + esc(visitContext.getCurrentBudgetRange()) + "\","
                + "\"travelDistanceKm\":" + visitContext.getTravelDistanceKm() + ","
                + "\"timeOfVisit\":\"" + esc(visitContext.getTimeOfVisit()) + "\","
                + "\"crowdTolerance\":\"" + esc(visitContext.getCrowdTolerance()) + "\","
                + "\"active\":" + storedVisitContext.isActive() + ","
                + "\"createdAt\":\"" + esc(storedVisitContext.getCreatedAt()) + "\""
                + "}";
    }

    private static String jsonArray(Set<String> values) {
        String body = values.stream().sorted().map(v -> "\"" + esc(v) + "\"").collect(Collectors.joining(","));
        return "[" + body + "]";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.6f", v);
    }
}
