package db;

public class DatabaseStatus {
    private final boolean enabled;
    private final String jdbcUrl;
    private final String message;

    public DatabaseStatus(boolean enabled, String jdbcUrl, String message) {
        this.enabled = enabled;
        this.jdbcUrl = jdbcUrl == null ? "" : jdbcUrl;
        this.message = message == null ? "" : message;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getMessage() {
        return message;
    }
}
