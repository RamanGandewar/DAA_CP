package model;

import java.util.Locale;

public class SocialPreference {
    private final String usuallyVisitWith;
    private final String preferredSeating;

    public SocialPreference(String usuallyVisitWith, String preferredSeating) {
        this.usuallyVisitWith = normalize(usuallyVisitWith);
        this.preferredSeating = normalize(preferredSeating);
    }

    public static SocialPreference empty() {
        return new SocialPreference("", "");
    }

    public String getUsuallyVisitWith() {
        return usuallyVisitWith;
    }

    public String getPreferredSeating() {
        return preferredSeating;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
