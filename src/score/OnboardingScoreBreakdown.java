package score;

import model.ProfileTag;

public class OnboardingScoreBreakdown {
    private final ProfileTag profileTag;
    private final double distanceScore;
    private final double budgetCompatibility;
    private final double categoryMatch;
    private final double ambienceMatch;
    private final double dynamicContextMatch;
    private final double userProfileScore;
    private final double finalMatchScore;
    private final double finalPenaltyScore;

    public OnboardingScoreBreakdown(ProfileTag profileTag,
                                    double distanceScore,
                                    double budgetCompatibility,
                                    double categoryMatch,
                                    double ambienceMatch,
                                    double dynamicContextMatch,
                                    double userProfileScore,
                                    double finalMatchScore,
                                    double finalPenaltyScore) {
        this.profileTag = profileTag;
        this.distanceScore = distanceScore;
        this.budgetCompatibility = budgetCompatibility;
        this.categoryMatch = categoryMatch;
        this.ambienceMatch = ambienceMatch;
        this.dynamicContextMatch = dynamicContextMatch;
        this.userProfileScore = userProfileScore;
        this.finalMatchScore = finalMatchScore;
        this.finalPenaltyScore = finalPenaltyScore;
    }

    public ProfileTag getProfileTag() { return profileTag; }
    public double getDistanceScore() { return distanceScore; }
    public double getBudgetCompatibility() { return budgetCompatibility; }
    public double getCategoryMatch() { return categoryMatch; }
    public double getAmbienceMatch() { return ambienceMatch; }
    public double getDynamicContextMatch() { return dynamicContextMatch; }
    public double getUserProfileScore() { return userProfileScore; }
    public double getFinalMatchScore() { return finalMatchScore; }
    public double getFinalPenaltyScore() { return finalPenaltyScore; }
}
