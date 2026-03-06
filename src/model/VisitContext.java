package model;

import java.util.Locale;

public class VisitContext {
    private final String purposeOfVisit;
    private final String currentBudgetRange;
    private final int travelDistanceKm;
    private final String timeOfVisit;
    private final String crowdTolerance;

    public VisitContext(String purposeOfVisit,
                        String currentBudgetRange,
                        int travelDistanceKm,
                        String timeOfVisit,
                        String crowdTolerance) {
        this.purposeOfVisit = norm(purposeOfVisit);
        this.currentBudgetRange = norm(currentBudgetRange);
        this.travelDistanceKm = travelDistanceKm;
        this.timeOfVisit = norm(timeOfVisit);
        this.crowdTolerance = norm(crowdTolerance);
    }

    public static VisitContext empty() {
        return new VisitContext("", "", 5, "", "");
    }

    public boolean isProvided() {
        return !purposeOfVisit.isBlank() || !currentBudgetRange.isBlank() || !timeOfVisit.isBlank();
    }

    public String getPurposeOfVisit() { return purposeOfVisit; }
    public String getCurrentBudgetRange() { return currentBudgetRange; }
    public int getTravelDistanceKm() { return travelDistanceKm; }
    public String getTimeOfVisit() { return timeOfVisit; }
    public String getCrowdTolerance() { return crowdTolerance; }

    private static String norm(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
