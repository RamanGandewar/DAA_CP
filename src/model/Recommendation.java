package model;

public class Recommendation {
    private final Cafe cafe;
    private final double score;
    private final double distanceKm;
    private final double cuisineMatchRatio;
    private final String explanation;
    private final ProfileTag profileTag;

    public Recommendation(Cafe cafe, double score, double distanceKm, double cuisineMatchRatio) {
        this(cafe, score, distanceKm, cuisineMatchRatio, "", null);
    }

    public Recommendation(Cafe cafe,
                          double score,
                          double distanceKm,
                          double cuisineMatchRatio,
                          String explanation,
                          ProfileTag profileTag) {
        this.cafe = cafe;
        this.score = score;
        this.distanceKm = distanceKm;
        this.cuisineMatchRatio = cuisineMatchRatio;
        this.explanation = explanation == null ? "" : explanation;
        this.profileTag = profileTag;
    }

    public Cafe getCafe() { return cafe; }
    public double getScore() { return score; }
    public double getDistanceKm() { return distanceKm; }
    public double getCuisineMatchRatio() { return cuisineMatchRatio; }
    public String getExplanation() { return explanation; }
    public ProfileTag getProfileTag() { return profileTag; }
}
