package model;

public class RecommendationExplanationRecord {
    private final long id;
    private final long recommendationHistoryId;
    private final String cafeId;
    private final int rankPosition;
    private final String explanationText;
    private final String createdAt;

    public RecommendationExplanationRecord(long id,
                                           long recommendationHistoryId,
                                           String cafeId,
                                           int rankPosition,
                                           String explanationText,
                                           String createdAt) {
        this.id = id;
        this.recommendationHistoryId = recommendationHistoryId;
        this.cafeId = cafeId == null ? "" : cafeId;
        this.rankPosition = rankPosition;
        this.explanationText = explanationText == null ? "" : explanationText;
        this.createdAt = createdAt == null ? "" : createdAt;
    }

    public long getId() {
        return id;
    }

    public long getRecommendationHistoryId() {
        return recommendationHistoryId;
    }

    public String getCafeId() {
        return cafeId;
    }

    public int getRankPosition() {
        return rankPosition;
    }

    public String getExplanationText() {
        return explanationText;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
