package score;

import model.Cafe;
import model.CafeInsights;
import model.DietaryPreference;
import model.ProfileTag;
import model.SearchQuery;
import model.UserProfile;
import model.VisitContext;

public class OnboardingScorer {
    private final OnboardingWeights weights;

    public OnboardingScorer(OnboardingWeights weights) {
        this.weights = weights;
    }

    public OnboardingScoreBreakdown score(Cafe cafe, CafeInsights insights, SearchQuery query, double distanceKm) {
        UserProfile profile = query.getUserProfile();
        VisitContext context = query.getVisitContext();
        ProfileTag tag = classifyProfile(profile, context);

        double distanceScore = closenessScore(distanceKm, Math.max(1, context.getTravelDistanceKm()));
        double budgetScore = budgetCompatibility(cafe.getAvgPrice(), context.getCurrentBudgetRange());
        double categoryMatch = categoryMatch(tag, profile.getPreferredCafeType(), context.getPurposeOfVisit(), insights, distanceKm);
        double ambienceMatch = ambienceMatch(profile.getMusicPreference(), profile.getLightingPreference(), insights);
        double dynamicMatch = dynamicContextMatch(context, insights, distanceKm);
        double profileScore = userProfileScore(profile, cafe, insights, distanceKm);
        double mismatchPenalty = intentMismatchPenalty(context, insights, distanceKm);

        double finalMatch = clamp01(
                weights.getDistance() * distanceScore
                        + weights.getBudget() * budgetScore
                        + weights.getCategory() * categoryMatch
                        + weights.getAmbience() * ambienceMatch
                        + weights.getDynamicContext() * dynamicMatch
                        + weights.getUserProfile() * profileScore
        );
        finalMatch = clamp01(finalMatch - mismatchPenalty);
        double finalPenalty = 1.0 - finalMatch;

        return new OnboardingScoreBreakdown(tag, distanceScore, budgetScore, categoryMatch, ambienceMatch, dynamicMatch, profileScore, finalMatch, finalPenalty);
    }

    public static ProfileTag classifyProfile(UserProfile profile, VisitContext context) {
        double workScore = 0;
        double socialScore = 0;
        double dateScore = 0;
        double quickScore = 0;

        if (contains(profile.getPreferredCafeType(), "quiet") || contains(profile.getPreferredCafeType(), "work")) workScore += 3;
        if (contains(profile.getPreferredCafeType(), "social") || contains(profile.getPreferredCafeType(), "hangout")) socialScore += 3;
        if (contains(profile.getPreferredCafeType(), "premium") || contains(profile.getPreferredCafeType(), "aesthetic")) dateScore += 3;
        if (context.getTravelDistanceKm() <= 1) quickScore += 2;

        if (contains(context.getPurposeOfVisit(), "work") || contains(context.getPurposeOfVisit(), "study") || contains(context.getPurposeOfVisit(), "meeting")) workScore += 4;
        if (contains(context.getPurposeOfVisit(), "hangout")) socialScore += 4;
        if (contains(context.getPurposeOfVisit(), "date")) dateScore += 4;
        if (contains(context.getPurposeOfVisit(), "coffee break")) quickScore += 4;

        if (contains(profile.getUsuallyVisitWith(), "friends")) socialScore += 2;
        if (contains(profile.getUsuallyVisitWith(), "partner")) dateScore += 2;
        if (contains(profile.getUsuallyVisitWith(), "alone") || contains(profile.getUsuallyVisitWith(), "colleagues")) workScore += 1;

        if (quickScore >= workScore && quickScore >= socialScore && quickScore >= dateScore) return ProfileTag.QUICK_COFFEE;
        if (dateScore >= workScore && dateScore >= socialScore) return ProfileTag.DATE_AESTHETIC;
        if (socialScore >= workScore) return ProfileTag.SOCIAL_HANGOUT;
        return ProfileTag.STUDY_WORK;
    }

    private double categoryMatch(ProfileTag tag,
                                 String preferredCafeType,
                                 String purpose,
                                 CafeInsights insights,
                                 double distanceKm) {
        double work = workCategoryFit(insights);
        double social = socialCategoryFit(insights);
        double aesthetic = aestheticCategoryFit(insights);
        double quick = quickCategoryFit(insights, distanceKm);

        double byTag;
        switch (tag) {
            case QUICK_COFFEE -> byTag = quick;
            case DATE_AESTHETIC -> byTag = aesthetic;
            case SOCIAL_HANGOUT -> byTag = social;
            default -> byTag = work;
        }

        double byType = 0.5;
        if (contains(preferredCafeType, "quiet") || contains(preferredCafeType, "work")) byType = work;
        if (contains(preferredCafeType, "social") || contains(preferredCafeType, "hangout")) byType = social;
        if (contains(preferredCafeType, "premium") || contains(preferredCafeType, "aesthetic")) byType = aesthetic;

        double byPurpose = 0.5;
        if (contains(purpose, "work") || contains(purpose, "study") || contains(purpose, "meeting")) byPurpose = work;
        if (contains(purpose, "hangout")) byPurpose = social;
        if (contains(purpose, "date")) byPurpose = aesthetic;
        if (contains(purpose, "coffee break")) byPurpose = quick;

        return clamp01((byTag * 0.25) + (byType * 0.15) + (byPurpose * 0.60));
    }

    private double ambienceMatch(String musicPreference, String lightingPreference, CafeInsights insights) {
        double music = 0.6;
        String acoustic = insights.getAcousticProfile().toLowerCase();
        if (contains(musicPreference, "silent")) {
            music = acoustic.contains("library quiet") ? 1.0 : acoustic.contains("lo-fi") ? 0.7 : 0.3;
        } else if (contains(musicPreference, "light")) {
            music = acoustic.contains("lo-fi") ? 1.0 : acoustic.contains("library quiet") ? 0.7 : 0.5;
        } else if (contains(musicPreference, "loud") || contains(musicPreference, "party")) {
            music = acoustic.contains("active chatter") ? 1.0 : 0.4;
        }

        double lighting = 0.6;
        String sunlight = insights.getSunlightLabel().toLowerCase();
        if (contains(lightingPreference, "bright")) {
            lighting = sunlight.contains("golden") ? 1.0 : sunlight.contains("balanced") ? 0.8 : 0.6;
        } else if (contains(lightingPreference, "cozy")) {
            lighting = sunlight.contains("shade") ? 0.95 : sunlight.contains("balanced") ? 0.8 : 0.6;
        } else if (contains(lightingPreference, "aesthetic") || contains(lightingPreference, "dim")) {
            boolean vibeHit = insights.getVibeTags().contains("#darkacademia") || insights.getVibeTags().contains("#vintagecorners");
            lighting = vibeHit ? 1.0 : 0.7;
        }

        return clamp01((music + lighting) / 2.0);
    }

    private double dynamicContextMatch(VisitContext context, CafeInsights insights, double distanceKm) {
        double purposeFit = 0.6;
        if (contains(context.getPurposeOfVisit(), "work") || contains(context.getPurposeOfVisit(), "study")) {
            purposeFit = workCategoryFit(insights);
        } else if (contains(context.getPurposeOfVisit(), "hangout")) {
            purposeFit = socialCategoryFit(insights);
        } else if (contains(context.getPurposeOfVisit(), "date")) {
            purposeFit = aestheticCategoryFit(insights);
        } else if (contains(context.getPurposeOfVisit(), "coffee break")) {
            purposeFit = quickCategoryFit(insights, distanceKm);
        } else if (contains(context.getPurposeOfVisit(), "meeting")) {
            purposeFit = clamp01((workCategoryFit(insights) + socialCategoryFit(insights)) / 2.0);
        }

        double crowdFit = 0.6;
        String acoustic = insights.getAcousticProfile().toLowerCase();
        if (contains(context.getCrowdTolerance(), "quiet")) {
            crowdFit = acoustic.contains("library quiet") ? 1.0 : acoustic.contains("lo-fi") ? 0.7 : 0.2;
        } else if (contains(context.getCrowdTolerance(), "moderate")) {
            crowdFit = acoustic.contains("lo-fi") ? 1.0 : 0.7;
        } else if (contains(context.getCrowdTolerance(), "lively")) {
            crowdFit = acoustic.contains("active chatter") ? 1.0 : 0.4;
        }

        double timeFit = timeOfDayFit(context.getTimeOfVisit(), insights);
        double distanceFit = closenessScore(distanceKm, Math.max(1, context.getTravelDistanceKm()));
        return clamp01((purposeFit * 0.50) + (crowdFit * 0.20) + (timeFit * 0.15) + (distanceFit * 0.15));
    }

    private double userProfileScore(UserProfile profile, Cafe cafe, CafeInsights insights, double distanceKm) {
        double dietFit = dietCompatibility(profile.getDietaryPreference(), cafe);
        double distanceFit = closenessScore(distanceKm, Math.max(1, profile.getPreferredDistanceKm()));
        double socialFit = socialHabitFit(profile.getUsuallyVisitWith(), insights);
        double seatingFit = seatingFit(profile.getPreferredSeating(), insights);
        return clamp01((dietFit * 0.4) + (distanceFit * 0.3) + (socialFit * 0.2) + (seatingFit * 0.1));
    }

    private double workCategoryFit(CafeInsights insights) {
        double workability = insights.getWorkabilityScore() / 10.0;
        double acoustic = insights.getAcousticProfile().equalsIgnoreCase("Library Quiet") ? 1.0 : 0.65;
        double meetingReadiness = insights.getMeetingScore() / 10.0;
        return clamp01((workability * 0.55) + (acoustic * 0.20) + (meetingReadiness * 0.25));
    }

    private double socialCategoryFit(CafeInsights insights) {
        double chairs = insights.getChairScore() / 10.0;
        double acoustic = insights.getAcousticProfile().equalsIgnoreCase("Active Chatter") ? 1.0 : 0.6;
        double hangout = insights.getHangoutScore() / 10.0;
        return clamp01((chairs * 0.30) + (acoustic * 0.25) + (hangout * 0.45));
    }

    private double aestheticCategoryFit(CafeInsights insights) {
        double vibe = (insights.getVibeTags().contains("#darkacademia") || insights.getVibeTags().contains("#vintagecorners")) ? 1.0 : 0.7;
        double light = insights.getSunlightLabel().toLowerCase().contains("golden") ? 1.0 : 0.75;
        double dateFit = insights.getDateScore() / 10.0;
        double privacy = insights.getPrivacyScore() / 10.0;
        double aesthetic = insights.getAestheticScore() / 10.0;
        return clamp01((vibe * 0.20) + (light * 0.15) + (dateFit * 0.35) + (privacy * 0.15) + (aesthetic * 0.15));
    }

    private double quickCategoryFit(CafeInsights insights, double distanceKm) {
        double mobility = insights.getWalkabilityScore() / 10.0;
        double distance = clamp01(1.0 - (distanceKm / 5.0));
        double serviceSpeed = insights.getQuickServiceScore() / 10.0;
        return clamp01((mobility * 0.25) + (distance * 0.35) + (serviceSpeed * 0.40));
    }

    private double intentMismatchPenalty(VisitContext context, CafeInsights insights, double distanceKm) {
        double penalty = 0.0;
        String purpose = context.getPurposeOfVisit();
        String acoustic = insights.getAcousticProfile().toLowerCase();

        if (contains(purpose, "work") || contains(purpose, "study")) {
            if (socialCategoryFit(insights) > 0.78 && !acoustic.contains("library quiet")) {
                penalty += 0.18;
            }
        } else if (contains(purpose, "hangout")) {
            if (workCategoryFit(insights) > 0.82 && acoustic.contains("library quiet")) {
                penalty += 0.16;
            }
        } else if (contains(purpose, "date")) {
            if (aestheticCategoryFit(insights) < 0.68 || insights.getPrivacyScore() <= 4) {
                penalty += 0.20;
            }
        } else if (contains(purpose, "coffee break")) {
            if (distanceKm > Math.max(1, context.getTravelDistanceKm()) || insights.getQuickServiceScore() <= 4) {
                penalty += 0.20;
            }
        } else if (contains(purpose, "meeting")) {
            if (workCategoryFit(insights) < 0.45 || insights.getMeetingScore() <= 4) {
                penalty += 0.12;
            }
        }

        if (contains(context.getCrowdTolerance(), "quiet") && acoustic.contains("active chatter")) {
            penalty += 0.14;
        }
        if (contains(context.getCrowdTolerance(), "lively") && acoustic.contains("library quiet")) {
            penalty += 0.10;
        }

        return clamp01(penalty);
    }

    private double timeOfDayFit(String timeOfVisit, CafeInsights insights) {
        String sunlight = insights.getSunlightLabel().toLowerCase();
        if (contains(timeOfVisit, "morning")) {
            return sunlight.contains("golden") || sunlight.contains("balanced") ? 1.0 : 0.7;
        }
        if (contains(timeOfVisit, "afternoon")) {
            return sunlight.contains("balanced") ? 1.0 : 0.75;
        }
        if (contains(timeOfVisit, "evening")) {
            boolean warm = sunlight.contains("shade") || sunlight.contains("golden")
                    || insights.getVibeTags().contains("#darkacademia")
                    || insights.getVibeTags().contains("#vintagecorners");
            return warm ? 0.95 : 0.7;
        }
        if (contains(timeOfVisit, "late night")) {
            boolean cozy = insights.getAcousticProfile().equalsIgnoreCase("Lo-fi Beats")
                    || insights.getVibeTags().contains("#darkacademia");
            return cozy ? 0.9 : 0.65;
        }
        return 0.7;
    }

    private double closenessScore(double distanceKm, double targetKm) {
        if (targetKm <= 0) {
            return 0;
        }
        return clamp01(1.0 - (distanceKm / targetKm));
    }

    private double budgetCompatibility(double avgPrice, String budgetRange) {
        double target = switch (budgetRange) {
            case "low" -> 400.0;
            case "high" -> 1200.0;
            case "medium" -> 800.0;
            default -> 700.0;
        };
        double deviation = Math.abs(avgPrice - target) / Math.max(200.0, target);
        return clamp01(1.0 - deviation);
    }

    private double dietCompatibility(DietaryPreference pref, Cafe cafe) {
        return switch (pref) {
            case VEG -> cafe.isVeg() ? 1.0 : 0.0;
            case NON_VEG -> cafe.isNonVeg() ? 1.0 : 0.0;
            case VEGAN -> cafe.isVegan() ? 1.0 : 0.0;
            default -> 1.0;
        };
    }

    private double socialHabitFit(String usuallyVisitWith, CafeInsights insights) {
        if (contains(usuallyVisitWith, "alone")) {
            return insights.getAcousticProfile().equalsIgnoreCase("Library Quiet") ? 1.0 : 0.7;
        }
        if (contains(usuallyVisitWith, "friends")) {
            return socialCategoryFit(insights);
        }
        if (contains(usuallyVisitWith, "colleagues")) {
            return clamp01((workCategoryFit(insights) + socialCategoryFit(insights)) / 2.0);
        }
        if (contains(usuallyVisitWith, "partner")) {
            return aestheticCategoryFit(insights);
        }
        return 0.6;
    }

    private double seatingFit(String preferredSeating, CafeInsights insights) {
        if (contains(preferredSeating, "doesn't matter") || preferredSeating.isBlank()) {
            return 1.0;
        }
        String sunlight = insights.getSunlightLabel().toLowerCase();
        if (contains(preferredSeating, "outdoor")) {
            return sunlight.contains("golden") || sunlight.contains("shade") ? 0.95 : 0.6;
        }
        if (contains(preferredSeating, "indoor")) {
            return sunlight.contains("balanced") || sunlight.contains("shade") ? 0.95 : 0.7;
        }
        return 0.7;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.contains(needle);
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0;
        }
        if (v > 1) {
            return 1;
        }
        return v;
    }
}
