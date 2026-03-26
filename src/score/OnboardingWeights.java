package score;

public class OnboardingWeights {
    private final double distance;
    private final double budget;
    private final double category;
    private final double ambience;
    private final double dynamicContext;
    private final double userProfile;

    public OnboardingWeights(double distance,
                             double budget,
                             double category,
                             double ambience,
                             double dynamicContext,
                             double userProfile) {
        double sum = distance + budget + category + ambience + dynamicContext + userProfile;
        if (Math.abs(sum - 1.0) > 1e-9) {
            throw new IllegalArgumentException("Onboarding weights must sum to 1.0");
        }
        this.distance = distance;
        this.budget = budget;
        this.category = category;
        this.ambience = ambience;
        this.dynamicContext = dynamicContext;
        this.userProfile = userProfile;
    }

    public static OnboardingWeights defaults() {
        return new OnboardingWeights(0.16, 0.16, 0.22, 0.14, 0.22, 0.10);
    }

    public double getDistance() { return distance; }
    public double getBudget() { return budget; }
    public double getCategory() { return category; }
    public double getAmbience() { return ambience; }
    public double getDynamicContext() { return dynamicContext; }
    public double getUserProfile() { return userProfile; }
}
