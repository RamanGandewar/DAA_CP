package model;

public class AuthSession {
    private final long id;
    private final long userId;
    private final String sessionToken;
    private final String loginAt;
    private final String logoutAt;
    private final Double locationLat;
    private final Double locationLon;
    private final String locationSource;

    public AuthSession(long id,
                       long userId,
                       String sessionToken,
                       String loginAt,
                       String logoutAt,
                       Double locationLat,
                       Double locationLon,
                       String locationSource) {
        this.id = id;
        this.userId = userId;
        this.sessionToken = sessionToken == null ? "" : sessionToken;
        this.loginAt = loginAt == null ? "" : loginAt;
        this.logoutAt = logoutAt == null ? "" : logoutAt;
        this.locationLat = locationLat;
        this.locationLon = locationLon;
        this.locationSource = locationSource == null ? "unknown" : locationSource;
    }

    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public String getLoginAt() {
        return loginAt;
    }

    public String getLogoutAt() {
        return logoutAt;
    }

    public Double getLocationLat() {
        return locationLat;
    }

    public Double getLocationLon() {
        return locationLon;
    }

    public String getLocationSource() {
        return locationSource;
    }
}
