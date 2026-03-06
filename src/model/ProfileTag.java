package model;

public enum ProfileTag {
    STUDY_WORK("Study/Work Profile"),
    SOCIAL_HANGOUT("Social Hangout Profile"),
    DATE_AESTHETIC("Date/Aesthetic Profile"),
    QUICK_COFFEE("Quick Coffee Profile");

    private final String label;

    ProfileTag(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
