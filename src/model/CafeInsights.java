package model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CafeInsights {
    private final Set<String> vibeTags;
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

    public CafeInsights(Set<String> vibeTags,
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
                        int walkabilityScore) {
        this.vibeTags = new HashSet<>(vibeTags);
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
    }

    public Set<String> getVibeTags() { return Collections.unmodifiableSet(vibeTags); }
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

    private int clamp1to10(int v) {
        return Math.max(1, Math.min(10, v));
    }
}
