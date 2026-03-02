package model;

public class Recommendation {
    private final Cafe cafe;
    private final double score;
    private final double distanceKm;
    private final double cuisineMatchRatio;

    public Recommendation(Cafe cafe, double score, double distanceKm, double cuisineMatchRatio) {
        this.cafe = cafe;
        this.score = score;
        this.distanceKm = distanceKm;
        this.cuisineMatchRatio = cuisineMatchRatio;
    }

    public Cafe getCafe() { return cafe; }
    public double getScore() { return score; }
    public double getDistanceKm() { return distanceKm; }
    public double getCuisineMatchRatio() { return cuisineMatchRatio; }
}
