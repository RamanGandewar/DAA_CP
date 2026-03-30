package service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import data.GlobalStats;
import filter.ConstraintFilter;
import model.Cafe;
import model.CafeInsights;
import model.Recommendation;
import model.SearchQuery;
import rank.TopKSelector;
import score.OnboardingScoreBreakdown;
import score.OnboardingScorer;
import score.OnboardingWeights;
import score.ScoreCalculator;
import spatial.GeoUtils;
import spatial.KDTree;

public class RecommendationService {
    private final KDTree kdTree;
    private final ConstraintFilter constraintFilter;
    private final ScoreCalculator scoreCalculator;
    private final OnboardingScorer onboardingScorer;
    private final TopKSelector topKSelector;
    private final InsightsService insightsService;

    public RecommendationService(List<Cafe> allCafes, KDTree kdTree, GlobalStats stats, InsightsService insightsService) {
        this.kdTree = kdTree;
        this.constraintFilter = new ConstraintFilter();
        this.scoreCalculator = new ScoreCalculator(stats);
        this.onboardingScorer = new OnboardingScorer(OnboardingWeights.defaults());
        this.topKSelector = new TopKSelector();
        this.insightsService = insightsService;
    }

    public List<Recommendation> recommend(SearchQuery query) {
        List<Cafe> bboxCandidates = kdTree.rangeSearch(query.getUserLatitude(), query.getUserLongitude(), query.getRadiusKm());

        List<CafeDistance> withinRadius = new ArrayList<>();
        for (Cafe cafe : bboxCandidates) {
            double d = GeoUtils.haversineKm(query.getUserLatitude(), query.getUserLongitude(), cafe.getLatitude(), cafe.getLongitude());
            if (d <= query.getRadiusKm()) {
                withinRadius.add(new CafeDistance(cafe, d));
            }
        }

        List<Cafe> budgetFiltered = constraintFilter.byBudget(extractCafes(withinRadius), query.getBudget());
        List<Cafe> valid = constraintFilter.byDiet(budgetFiltered, query.getDietaryPreference());
        valid = applyExtendedFilters(valid, query);
        valid = applyIntentShortlist(valid, query);

        List<Recommendation> scored = score(valid, query);
        if (scored.isEmpty()) {
            return fallbackRecommendations(query);
        }

        return topKSelector.selectTopK(scored, query.getTopK());
    }

    private List<Recommendation> score(List<Cafe> cafes, SearchQuery query) {
        List<Recommendation> out = new ArrayList<>();
        for (Cafe cafe : cafes) {
            double distance = GeoUtils.haversineKm(query.getUserLatitude(), query.getUserLongitude(), cafe.getLatitude(), cafe.getLongitude());
            CafeInsights insights = insightsService.forCafe(cafe);
            double score;
            String explanation;
            String rankingReason;
            model.ProfileTag profileTag = null;

            if (query.hasOnboardingContext()) {
                OnboardingScoreBreakdown breakdown = onboardingScorer.score(cafe, insights, query, distance);
                score = breakdown.getFinalPenaltyScore();
                profileTag = breakdown.getProfileTag();
                explanation = buildOnboardingExplanation(cafe, query, distance, breakdown, false);
                rankingReason = buildRankingReason(query);
            } else {
                score = scoreCalculator.computeScore(cafe, distance, query.getRadiusKm(), query.getPreferredCuisines(), query.getWeights());
                explanation = buildClassicExplanation(cafe, query, distance);
                rankingReason = "Ranked using distance, budget, rating, and cuisine fit.";
            }

            double matchRatio = scoreCalculator.cuisineMatchRatio(query.getPreferredCuisines(), cafe);
            out.add(new Recommendation(cafe, score, distance, matchRatio, explanation, rankingReason, profileTag));
        }
        return out;
    }

    private List<Cafe> applyIntentShortlist(List<Cafe> cafes, SearchQuery query) {
        if (!query.hasOnboardingContext() || cafes.size() <= Math.max(8, query.getTopK())) {
            return cafes;
        }

        List<CafeIntentScore> intentScored = new ArrayList<>();
        for (Cafe cafe : cafes) {
            double distance = GeoUtils.haversineKm(query.getUserLatitude(), query.getUserLongitude(), cafe.getLatitude(), cafe.getLongitude());
            CafeInsights insights = insightsService.forCafe(cafe);
            intentScored.add(new CafeIntentScore(cafe, intentFit(query, insights, distance)));
        }

        intentScored.sort((a, b) -> Double.compare(b.intentScore, a.intentScore));
        int keepCount = Math.min(intentScored.size(), Math.max(query.getTopK() * 4, 12));
        List<Cafe> shortlisted = new ArrayList<>();
        for (int i = 0; i < keepCount; i++) {
            CafeIntentScore candidate = intentScored.get(i);
            if (candidate.intentScore >= 0.45 || i < Math.max(query.getTopK() * 2, 8)) {
                shortlisted.add(candidate.cafe);
            }
        }
        return shortlisted.isEmpty() ? cafes : shortlisted;
    }

    private List<Recommendation> fallbackRecommendations(SearchQuery query) {
        double expandedRadius = query.getRadiusKm() * 2;
        List<Cafe> bbox = kdTree.rangeSearch(query.getUserLatitude(), query.getUserLongitude(), expandedRadius);

        List<Recommendation> fallback = new ArrayList<>();
        for (Cafe cafe : bbox) {
            double distance = GeoUtils.haversineKm(query.getUserLatitude(), query.getUserLongitude(), cafe.getLatitude(), cafe.getLongitude());
            if (distance > expandedRadius) {
                continue;
            }

            double score;
            String explanation;
            String rankingReason;
            model.ProfileTag profileTag = null;
            if (query.hasOnboardingContext()) {
                CafeInsights insights = insightsService.forCafe(cafe);
                OnboardingScoreBreakdown breakdown = onboardingScorer.score(cafe, insights, query, distance);
                score = breakdown.getFinalPenaltyScore();
                profileTag = breakdown.getProfileTag();
                explanation = buildOnboardingExplanation(cafe, query, distance, breakdown, true);
                rankingReason = "Fallback ranking still prioritized your current visit intent.";
            } else {
                score = scoreCalculator.computeScore(cafe, distance, expandedRadius, query.getPreferredCuisines(), query.getWeights());
                explanation = buildFallbackExplanation(cafe, distance);
                rankingReason = "Fallback ranking used affordability and nearby availability.";
            }
            double matchRatio = scoreCalculator.cuisineMatchRatio(query.getPreferredCuisines(), cafe);
            fallback.add(new Recommendation(cafe, score, distance, matchRatio, explanation, rankingReason, profileTag));
        }

        if (fallback.isEmpty()) {
            return List.of();
        }

        fallback.sort((a, b) -> Double.compare(a.getCafe().getAvgPrice(), b.getCafe().getAvgPrice()));
        int k = Math.min(5, fallback.size());
        return fallback.subList(0, k);
    }

    private String buildOnboardingExplanation(Cafe cafe,
                                              SearchQuery query,
                                              double distanceKm,
                                              OnboardingScoreBreakdown breakdown,
                                              boolean fallback) {
        String purpose = humanize(query.getVisitContext().getPurposeOfVisit(), "current visit");
        String profile = breakdown.getProfileTag().getLabel();

        List<Reason> reasons = new ArrayList<>();
        reasons.add(new Reason(breakdown.getDistanceScore(),
                String.format("it is within %.1f km", distanceKm)));
        reasons.add(new Reason(breakdown.getBudgetCompatibility(),
                String.format("it fits your %s budget", humanize(query.getVisitContext().getCurrentBudgetRange(), "selected"))));
        reasons.add(new Reason(breakdown.getCategoryMatch(),
                String.format("it matches your %s profile", profile.toLowerCase())));
        reasons.add(new Reason(breakdown.getAmbienceMatch(),
                ambienceReason(query)));
        reasons.add(new Reason(breakdown.getDynamicContextMatch(),
                String.format("it suits your %s plan", purpose)));
        reasons.add(new Reason(breakdown.getUserProfileScore(),
                personalReason(query, cafe)));

        reasons.sort(Comparator.comparingDouble(Reason::score).reversed());
        List<String> topReasons = new ArrayList<>();
        for (Reason reason : reasons) {
            if (reason.score() >= 0.65 && !topReasons.contains(reason.text())) {
                topReasons.add(reason.text());
            }
            if (topReasons.size() == 3) {
                break;
            }
        }
        if (topReasons.isEmpty()) {
            topReasons.add(String.format("it is %.1f km away", distanceKm));
            topReasons.add(String.format("it aligns with your %s intent", purpose));
        }

        String prefix = fallback ? "Fallback recommendation because " : "Recommended because ";
        return prefix + joinReasons(topReasons) + ".";
    }

    private String buildClassicExplanation(Cafe cafe, SearchQuery query, double distanceKm) {
        List<String> reasons = new ArrayList<>();
        reasons.add(String.format("it is within %.1f km", distanceKm));
        if (cafe.getAvgPrice() <= query.getBudget()) {
            reasons.add(String.format("it is within your budget of Rs %.0f", query.getBudget()));
        }
        if (!query.getPreferredCuisines().isEmpty()) {
            reasons.add("it matches your cuisine preferences");
        }
        if (cafe.getRating() >= 4.0) {
            reasons.add(String.format("it has a strong %.1f/5 rating", cafe.getRating()));
        }
        return "Recommended because " + joinReasons(reasons) + ".";
    }

    private String buildFallbackExplanation(Cafe cafe, double distanceKm) {
        List<String> reasons = new ArrayList<>();
        reasons.add(String.format("it is still reasonably close at %.1f km", distanceKm));
        reasons.add(String.format("it stays affordable at around Rs %.0f", cafe.getAvgPrice()));
        if (cafe.getRating() >= 4.0) {
            reasons.add(String.format("it has a %.1f/5 rating", cafe.getRating()));
        }
        return "Fallback recommendation because " + joinReasons(reasons) + ".";
    }

    private String ambienceReason(SearchQuery query) {
        String music = humanize(query.getUserProfile().getMusicPreference(), "");
        String lighting = humanize(query.getUserProfile().getLightingPreference(), "");
        if (!music.isBlank() && !lighting.isBlank()) {
            return String.format("its ambience fits your %s music and %s lighting preference", music, lighting);
        }
        if (!music.isBlank()) {
            return String.format("its sound profile fits your %s music preference", music);
        }
        if (!lighting.isBlank()) {
            return String.format("its ambience fits your %s lighting preference", lighting);
        }
        return "its ambience aligns well with your preferences";
    }

    private String personalReason(SearchQuery query, Cafe cafe) {
        String visitWith = humanize(query.getUserProfile().getUsuallyVisitWith(), "");
        if (!visitWith.isBlank()) {
            return String.format("it fits how you usually visit cafes (%s)", visitWith);
        }
        if (query.getUserProfile().getDietaryPreference() != null && query.getUserProfile().getDietaryPreference() != model.DietaryPreference.ANY) {
            return "it aligns with your dietary preference";
        }
        return String.format("it balances distance and practicality for a %.0f rupee spend", cafe.getAvgPrice());
    }

    private String buildRankingReason(SearchQuery query) {
        String purpose = humanize(query.getVisitContext().getPurposeOfVisit(), "current visit");
        String crowd = humanize(query.getVisitContext().getCrowdTolerance(), "");
        String time = humanize(query.getVisitContext().getTimeOfVisit(), "");

        List<String> parts = new ArrayList<>();
        if (!purpose.isBlank()) {
            parts.add(purpose);
        }
        if (!crowd.isBlank()) {
            parts.add(crowd);
        }
        if (!time.isBlank()) {
            parts.add(time);
        }
        if (parts.isEmpty()) {
            return "Ranked using your current onboarding preferences.";
        }
        return "Ranked higher for: " + String.join(" + ", parts) + ".";
    }

    private String humanize(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.replace('_', ' ').toLowerCase();
    }

    private String joinReasons(List<String> reasons) {
        List<String> cleaned = new ArrayList<>();
        for (String reason : reasons) {
            if (reason != null && !reason.isBlank() && !cleaned.contains(reason)) {
                cleaned.add(reason);
            }
        }
        if (cleaned.isEmpty()) {
            return "it balances your selected factors well";
        }
        if (cleaned.size() == 1) {
            return cleaned.get(0);
        }
        if (cleaned.size() == 2) {
            return cleaned.get(0) + " and " + cleaned.get(1);
        }
        return cleaned.get(0) + ", " + cleaned.get(1) + ", and " + cleaned.get(2);
    }

    private record Reason(double score, String text) {
    }

    private double intentFit(SearchQuery query, CafeInsights insights, double distanceKm) {
        String purpose = query.getVisitContext().getPurposeOfVisit();
        String acoustic = insights.getAcousticProfile().toLowerCase();

        double fit;
        if (purpose.contains("work") || purpose.contains("study")) {
            fit = clamp01((insights.getWorkabilityScore() / 10.0) * 0.50
                    + (insights.getMeetingScore() / 10.0) * 0.25
                    + (acoustic.contains("library quiet") ? 0.25 : 0.08));
        } else if (purpose.contains("hangout")) {
            fit = clamp01((insights.getHangoutScore() / 10.0) * 0.50
                    + (insights.getChairScore() / 10.0) * 0.20
                    + (acoustic.contains("active chatter") ? 0.20 : 0.10)
                    + (insights.getWalkabilityScore() / 10.0) * 0.10);
        } else if (purpose.contains("date")) {
            fit = clamp01((insights.getDateScore() / 10.0) * 0.45
                    + (insights.getPrivacyScore() / 10.0) * 0.25
                    + (insights.getAestheticScore() / 10.0) * 0.20
                    + (insights.getSunlightLabel().toLowerCase().contains("golden") ? 0.10 : 0.04));
        } else if (purpose.contains("coffee break")) {
            fit = clamp01((1.0 - Math.min(distanceKm, 5.0) / 5.0) * 0.40
                    + (insights.getWalkabilityScore() / 10.0) * 0.25
                    + (insights.getQuickServiceScore() / 10.0) * 0.35);
        } else if (purpose.contains("meeting")) {
            fit = clamp01((insights.getMeetingScore() / 10.0) * 0.45
                    + (insights.getWorkabilityScore() / 10.0) * 0.30
                    + (insights.getChairScore() / 10.0) * 0.15
                    + (acoustic.contains("lo-fi") ? 0.10 : 0.05));
        } else {
            fit = 0.6;
        }

        if (query.getVisitContext().getCrowdTolerance().contains("quiet") && acoustic.contains("active chatter")) {
            fit -= 0.18;
        }
        if (query.getVisitContext().getCrowdTolerance().contains("lively") && acoustic.contains("library quiet")) {
            fit -= 0.12;
        }
        return clamp01(fit);
    }

    private double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private List<Cafe> applyExtendedFilters(List<Cafe> cafes, SearchQuery query) {
        List<Cafe> out = new ArrayList<>();
        for (Cafe cafe : cafes) {
            if (query.isIndependentOnly() && !insightsService.forCafe(cafe).isIndependent()) {
                continue;
            }
            if (!insightsService.matchesMenu(cafe, query.getMenuQuery())) {
                continue;
            }
            if (!insightsService.matchesVibe(cafe, query.getVibeTags())) {
                continue;
            }
            if (!insightsService.matchesAcoustic(cafe, query.getAcousticProfile())) {
                continue;
            }
            out.add(cafe);
        }
        return out;
    }

    private List<Cafe> extractCafes(List<CafeDistance> in) {
        List<Cafe> out = new ArrayList<>();
        for (CafeDistance cd : in) {
            out.add(cd.cafe);
        }
        return out;
    }

    private static class CafeDistance {
        final Cafe cafe;
        final double distance;

        CafeDistance(Cafe cafe, double distance) {
            this.cafe = cafe;
            this.distance = distance;
        }
    }

    private static class CafeIntentScore {
        final Cafe cafe;
        final double intentScore;

        CafeIntentScore(Cafe cafe, double intentScore) {
            this.cafe = cafe;
            this.intentScore = intentScore;
        }
    }
}
