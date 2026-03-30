package model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CafeInsights {
    private final Set<String> vibeTags;
    private final Set<String> occasionTags;
    private final int wifiScore;
    private final int outletScore;
    private final int chairScore;
    private final int workabilityScore;
    private final String acousticProfile;
    private final Set<String> altMilks;
    private final String roastery;
    private final String sunlightLabel;
    private final boolean independent;
    private final Set<String> menuItems;
    private final boolean bikeRack;
    private final int parkingScore;
    private final int walkabilityScore;
    private final int hangoutScore;
    private final int dateScore;
    private final int meetingScore;
    private final int quickServiceScore;
    private final int privacyScore;
    private final int aestheticScore;
    private final String aiSummary;

    public CafeInsights(Set<String> vibeTags,
                        Set<String> occasionTags,
                        int wifiScore,
                        int outletScore,
                        int chairScore,
                        String acousticProfile,
                        Set<String> altMilks,
                        String roastery,
                        String sunlightLabel,
                        boolean independent,
                        Set<String> menuItems,
                        boolean bikeRack,
                        int parkingScore,
                        int walkabilityScore,
                        int hangoutScore,
                        int dateScore,
                        int meetingScore,
                        int quickServiceScore,
                        int privacyScore,
                        int aestheticScore,
                        String aiSummary) {
        this.vibeTags = new HashSet<>(vibeTags);
        this.occasionTags = new HashSet<>(occasionTags);
        this.wifiScore = clamp1to10(wifiScore);
        this.outletScore = clamp1to10(outletScore);
        this.chairScore = clamp1to10(chairScore);
        this.workabilityScore = Math.max(1, Math.min(10, (this.wifiScore + this.outletScore + this.chairScore) / 3));
        this.acousticProfile = acousticProfile;
        this.altMilks = new HashSet<>(altMilks);
        this.roastery = roastery;
        this.sunlightLabel = sunlightLabel;
        this.independent = independent;
        this.menuItems = new HashSet<>(menuItems);
        this.bikeRack = bikeRack;
        this.parkingScore = clamp1to10(parkingScore);
        this.walkabilityScore = clamp1to10(walkabilityScore);
        this.hangoutScore = clamp1to10(hangoutScore);
        this.dateScore = clamp1to10(dateScore);
        this.meetingScore = clamp1to10(meetingScore);
        this.quickServiceScore = clamp1to10(quickServiceScore);
        this.privacyScore = clamp1to10(privacyScore);
        this.aestheticScore = clamp1to10(aestheticScore);
        this.aiSummary = aiSummary == null ? "" : aiSummary;
    }

    public Set<String> getVibeTags() { return Collections.unmodifiableSet(vibeTags); }
    public Set<String> getOccasionTags() { return Collections.unmodifiableSet(occasionTags); }
    public int getWifiScore() { return wifiScore; }
    public int getOutletScore() { return outletScore; }
    public int getChairScore() { return chairScore; }
    public int getWorkabilityScore() { return workabilityScore; }
    public String getAcousticProfile() { return acousticProfile; }
    public Set<String> getAltMilks() { return Collections.unmodifiableSet(altMilks); }
    public String getRoastery() { return roastery; }
    public String getSunlightLabel() { return sunlightLabel; }
    public boolean isIndependent() { return independent; }
    public Set<String> getMenuItems() { return Collections.unmodifiableSet(menuItems); }
    public boolean hasBikeRack() { return bikeRack; }
    public int getParkingScore() { return parkingScore; }
    public int getWalkabilityScore() { return walkabilityScore; }
    public int getHangoutScore() { return hangoutScore; }
    public int getDateScore() { return dateScore; }
    public int getMeetingScore() { return meetingScore; }
    public int getQuickServiceScore() { return quickServiceScore; }
    public int getPrivacyScore() { return privacyScore; }
    public int getAestheticScore() { return aestheticScore; }
    public String getAiSummary() { return aiSummary; }

    private int clamp1to10(int v) {
        return Math.max(1, Math.min(10, v));
    }
}
