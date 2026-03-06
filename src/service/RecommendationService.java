package service;

import java.util.ArrayList;
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
                explanation = buildOnboardingExplanation(query, distance, breakdown);
            } else {
                score = scoreCalculator.computeScore(cafe, distance, query.getRadiusKm(), query.getPreferredCuisines(), query.getWeights());
                explanation = "Recommended due to strong distance, budget, and category score.";
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
                explanation = buildOnboardingExplanation(query, distance, breakdown);
            } else {
                score = scoreCalculator.computeScore(cafe, distance, expandedRadius, query.getPreferredCuisines(), query.getWeights());
                explanation = "Fallback match based on distance and affordability in expanded radius.";
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

    private String buildOnboardingExplanation(SearchQuery query, double distanceKm, OnboardingScoreBreakdown breakdown) {
        String purpose = query.getVisitContext().getPurposeOfVisit().isBlank() ? "current plan" : query.getVisitContext().getPurposeOfVisit();
        String profile = breakdown.getProfileTag().getLabel();
        return String.format(
                "Recommended because it matches your %s, fits your %s intent, and is within %.1f km.",
                profile,
                purpose,
                distanceKm
        );
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
