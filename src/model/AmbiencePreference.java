package model;

import java.util.Locale;

public class AmbiencePreference {
    private final String musicPreference;
    private final String lightingPreference;

    public AmbiencePreference(String musicPreference, String lightingPreference) {
        this.musicPreference = normalize(musicPreference);
        this.lightingPreference = normalize(lightingPreference);
    }

    public static AmbiencePreference empty() {
        return new AmbiencePreference("", "");
    }

    public String getMusicPreference() {
        return musicPreference;
    }

    public String getLightingPreference() {
        return lightingPreference;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
