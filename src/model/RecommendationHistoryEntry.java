package model;

public class RecommendationHistoryEntry {
    private final long id;
    private final long userId;
    private final Long visitContextId;
    private final String source;
    private final double latitude;
    private final double longitude;
    private final double radiusKm;
    private final double budget;
    private final int topK;
    private final int resultCount;
    private final String createdAt;

    public RecommendationHistoryEntry(long id,
                                      long userId,
                                      Long visitContextId,
                                      String source,
                                      double latitude,
                                      double longitude,
                                      double radiusKm,
                                      double budget,
                                      int topK,
                                      int resultCount,
                                      String createdAt) {
        this.id = id;
        this.userId = userId;
        this.visitContextId = visitContextId;
        this.source = source == null ? "" : source;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusKm = radiusKm;
        this.budget = budget;
        this.topK = topK;
        this.resultCount = resultCount;
        this.createdAt = createdAt == null ? "" : createdAt;
    }

    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public Long getVisitContextId() {
        return visitContextId;
    }

    public String getSource() {
        return source;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getRadiusKm() {
        return radiusKm;
    }

    public double getBudget() {
        return budget;
    }

    public int getTopK() {
        return topK;
    }

    public int getResultCount() {
        return resultCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
