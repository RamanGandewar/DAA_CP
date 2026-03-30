package model;

import java.util.HashSet;
import java.util.Set;

public class CafeEnrichment {
    private final Set<String> occasionTags;
    private final int hangoutScore;
    private final int dateScore;
    private final int workScore;
    private final int meetingScore;
    private final int quickServiceScore;
    private final int privacyScore;
    private final int aestheticScore;
    private final String aiSummary;

    public CafeEnrichment(Set<String> occasionTags,
                          int hangoutScore,
                          int dateScore,
                          int workScore,
                          int meetingScore,
                          int quickServiceScore,
                          int privacyScore,
                          int aestheticScore,
                          String aiSummary) {
        this.occasionTags = occasionTags == null ? new HashSet<>() : new HashSet<>(occasionTags);
        this.hangoutScore = clamp1to10(hangoutScore);
        this.dateScore = clamp1to10(dateScore);
        this.workScore = clamp1to10(workScore);
        this.meetingScore = clamp1to10(meetingScore);
        this.quickServiceScore = clamp1to10(quickServiceScore);
        this.privacyScore = clamp1to10(privacyScore);
        this.aestheticScore = clamp1to10(aestheticScore);
        this.aiSummary = aiSummary == null ? "" : aiSummary.trim();
    }

    public Set<String> getOccasionTags() { return occasionTags; }
    public int getHangoutScore() { return hangoutScore; }
    public int getDateScore() { return dateScore; }
    public int getWorkScore() { return workScore; }
    public int getMeetingScore() { return meetingScore; }
    public int getQuickServiceScore() { return quickServiceScore; }
    public int getPrivacyScore() { return privacyScore; }
    public int getAestheticScore() { return aestheticScore; }
    public String getAiSummary() { return aiSummary; }

    private int clamp1to10(int value) {
        return Math.max(1, Math.min(10, value));
    }
}
