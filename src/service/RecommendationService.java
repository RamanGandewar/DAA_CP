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
            model.ProfileTag profileTag = null;

            if (query.hasOnboardingContext()) {
                OnboardingScoreBreakdown breakdown = onboardingScorer.score(cafe, insights, query, distance);
                score = breakdown.getFinalPenaltyScore();
                profileTag = breakdown.getProfileTag();
                explanation = buildOnboardingExplanation(cafe, query, distance, breakdown, false);
            } else {
                score = scoreCalculator.computeScore(cafe, distance, query.getRadiusKm(), query.getPreferredCuisines(), query.getWeights());
                explanation = buildClassicExplanation(cafe, query, distance);
            }

            double matchRatio = scoreCalculator.cuisineMatchRatio(query.getPreferredCuisines(), cafe);
            out.add(new Recommendation(cafe, score, distance, matchRatio, explanation, profileTag));
        }
        return out;
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
            model.ProfileTag profileTag = null;
            if (query.hasOnboardingContext()) {
                CafeInsights insights = insightsService.forCafe(cafe);
                OnboardingScoreBreakdown breakdown = onboardingScorer.score(cafe, insights, query, distance);
                score = breakdown.getFinalPenaltyScore();
                profileTag = breakdown.getProfileTag();
                explanation = buildOnboardingExplanation(cafe, query, distance, breakdown, true);
            } else {
                score = scoreCalculator.computeScore(cafe, distance, expandedRadius, query.getPreferredCuisines(), query.getWeights());
                explanation = buildFallbackExplanation(cafe, distance);
            }
            double matchRatio = scoreCalculator.cuisineMatchRatio(query.getPreferredCuisines(), cafe);
            fallback.add(new Recommendation(cafe, score, distance, matchRatio, explanation, profileTag));
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
}
