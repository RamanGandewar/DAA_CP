package service;

import java.util.ArrayList;
import java.util.List;
import data.GlobalStats;
import filter.ConstraintFilter;
import model.Cafe;
import model.Recommendation;
import model.SearchQuery;
import rank.TopKSelector;
import score.ScoreCalculator;
import spatial.GeoUtils;
import spatial.KDTree;

public class RecommendationService {
    private final KDTree kdTree;
    private final ConstraintFilter constraintFilter;
    private final ScoreCalculator scoreCalculator;
    private final TopKSelector topKSelector;
    private final InsightsService insightsService;

    public RecommendationService(List<Cafe> allCafes, KDTree kdTree, GlobalStats stats, InsightsService insightsService) {
        this.kdTree = kdTree;
        this.constraintFilter = new ConstraintFilter();
        this.scoreCalculator = new ScoreCalculator(stats);
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
            double score = scoreCalculator.computeScore(cafe, distance, query.getRadiusKm(), query.getPreferredCuisines(), query.getWeights());
            double matchRatio = scoreCalculator.cuisineMatchRatio(query.getPreferredCuisines(), cafe);
            out.add(new Recommendation(cafe, score, distance, matchRatio));
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

            double score = scoreCalculator.computeScore(cafe, distance, expandedRadius, query.getPreferredCuisines(), query.getWeights());
            double matchRatio = scoreCalculator.cuisineMatchRatio(query.getPreferredCuisines(), cafe);
            fallback.add(new Recommendation(cafe, score, distance, matchRatio));
        }

        if (fallback.isEmpty()) {
            return List.of();
        }

        fallback.sort((a, b) -> Double.compare(a.getCafe().getAvgPrice(), b.getCafe().getAvgPrice()));
        int k = Math.min(5, fallback.size());
        return fallback.subList(0, k);
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
