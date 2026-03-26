package db;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import model.AdminOverview;
import model.AppUser;
import model.AuthSession;
import model.UserSummary;

public class SQLiteAuthRepository implements AuthRepository {
    @Override
    public AppUser registerUser(String name, String email, String password) {
        validateEmailPassword(email, password);
        final String normalizedEmail = normalizeEmail(email);
        if (findUserByEmail(normalizedEmail).isPresent()) {
            throw new RepositoryException("An account with this email already exists.", new IllegalStateException("duplicate email"));
        }
        final String sql = """
                INSERT INTO app_users(
                    user_key, display_name, email, password_hash, role, is_active, onboarding_completed, login_count
                )
                VALUES (?, ?, ?, ?, 'USER', 1, 0, 0)
                """;
        final String userKey = buildUserKey(name, normalizedEmail);
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, userKey);
            ps.setString(2, safe(name));
            ps.setString(3, normalizedEmail);
            ps.setString(4, PasswordHasher.hashPassword(password));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return requireUserById(connection, keys.getLong(1));
                }
            }
            throw new SQLException("Failed to create user account.");
        } catch (SQLException e) {
            throw new RepositoryException("Unable to register user.", e);
        }
    }

    @Override
    public Optional<AppUser> findUserByEmail(String email) {
        final String sql = """
                SELECT id, user_key, display_name, email, role, is_active, onboarding_completed,
                       last_login_at, login_count, created_at, updated_at
                FROM app_users
                WHERE email = ?
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, normalizeEmail(email));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapAppUser(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Unable to find user by email.", e);
        }
    }

    @Override
    public Optional<AppUser> findUserBySessionToken(String sessionToken) {
        final String sql = """
                SELECT u.id, u.user_key, u.display_name, u.email, u.role, u.is_active, u.onboarding_completed,
                       u.last_login_at, u.login_count, u.created_at, u.updated_at
                FROM app_users u
                JOIN user_sessions s ON s.user_id = u.id
                WHERE s.session_token = ? AND s.logout_at IS NULL
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, safe(sessionToken));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapAppUser(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Unable to resolve session user.", e);
        }
    }

    @Override
    public AuthSession login(String email, String password) {
        validateEmailPassword(email, password);
        final String normalizedEmail = normalizeEmail(email);
        final String sql = """
                SELECT id, user_key, display_name, email, password_hash, role, is_active, onboarding_completed,
                       last_login_at, login_count, created_at, updated_at
                FROM app_users
                WHERE email = ?
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, normalizedEmail);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RepositoryException("Invalid email or password.", new IllegalArgumentException("unknown email"));
                }
                String storedHash = rs.getString("password_hash");
                if (!PasswordHasher.verifyPassword(password, storedHash)) {
                    throw new RepositoryException("Invalid email or password.", new IllegalArgumentException("invalid password"));
                }
                if (rs.getInt("is_active") != 1) {
                    throw new RepositoryException("This account is inactive.", new IllegalStateException("inactive account"));
                }
                AppUser user = mapAppUser(rs);
                return createSession(connection, user.getId());
            }
        } catch (SQLException e) {
            throw new RepositoryException("Unable to login.", e);
        }
    }

    @Override
    public void logout(String sessionToken) {
        final String sql = """
                UPDATE user_sessions
                SET logout_at = CURRENT_TIMESTAMP
                WHERE session_token = ? AND logout_at IS NULL
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, safe(sessionToken));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Unable to logout session.", e);
        }
    }

    @Override
    public Optional<AuthSession> findActiveSession(String sessionToken) {
        final String sql = """
                SELECT id, user_id, session_token, login_at, logout_at, location_lat, location_lon, location_source
                FROM user_sessions
                WHERE session_token = ? AND logout_at IS NULL
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, safe(sessionToken));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSession(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Unable to fetch active session.", e);
        }
    }

    @Override
    public void updateSessionLocation(String sessionToken, Double lat, Double lon, String locationSource) {
        final String sql = """
                UPDATE user_sessions
                SET location_lat = ?, location_lon = ?, location_source = ?
                WHERE session_token = ? AND logout_at IS NULL
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            if (lat == null) {
                ps.setNull(1, java.sql.Types.REAL);
            } else {
                ps.setDouble(1, lat);
            }
            if (lon == null) {
                ps.setNull(2, java.sql.Types.REAL);
            } else {
                ps.setDouble(2, lon);
            }
            ps.setString(3, normalizeLocationSource(locationSource));
            ps.setString(4, safe(sessionToken));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Unable to update session location.", e);
        }
    }

    @Override
    public AdminOverview getAdminOverview() {
        try (Connection connection = DatabaseManager.getConnection()) {
            int totalUsers = scalarCount(connection, "SELECT COUNT(*) FROM app_users WHERE role = 'USER'");
            int totalAdmins = scalarCount(connection, "SELECT COUNT(*) FROM app_users WHERE role = 'ADMIN'");
            int totalLogins = scalarCount(connection, "SELECT COALESCE(SUM(login_count), 0) FROM app_users");
            int totalSearches = scalarCount(connection, "SELECT COUNT(*) FROM recommendation_history");
            int onboardingCompleted = scalarCount(connection, "SELECT COUNT(*) FROM app_users WHERE onboarding_completed = 1");
            List<UserSummary> users = loadUserSummaries(connection);
            return new AdminOverview(totalUsers, totalAdmins, totalLogins, totalSearches, onboardingCompleted, users);
        } catch (SQLException e) {
            throw new RepositoryException("Unable to build admin overview.", e);
        }
    }

    private AuthSession createSession(Connection connection, long userId) throws SQLException {
        final String token = randomToken();
        final String insertSql = """
                INSERT INTO user_sessions(user_id, session_token, location_source)
                VALUES (?, ?, 'unknown')
                """;
        final String updateUserSql = """
                UPDATE app_users
                SET last_login_at = CURRENT_TIMESTAMP,
                    login_count = COALESCE(login_count, 0) + 1
                WHERE id = ?
                """;
        connection.setAutoCommit(false);
        try (PreparedStatement insert = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement updateUser = connection.prepareStatement(updateUserSql)) {
            insert.setLong(1, userId);
            insert.setString(2, token);
            insert.executeUpdate();
            updateUser.setLong(1, userId);
            updateUser.executeUpdate();
            long sessionId;
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to create auth session.");
                }
                sessionId = keys.getLong(1);
            }
            connection.commit();
            return requireSessionById(connection, sessionId);
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private int scalarCount(Connection connection, String sql) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private List<UserSummary> loadUserSummaries(Connection connection) throws SQLException {
        final String sql = """
                SELECT u.id,
                       u.display_name,
                       u.email,
                       u.role,
                       u.is_active,
                       u.onboarding_completed,
                       u.login_count,
                       u.last_login_at,
                       u.created_at,
                       up.preferred_cafe_type,
                       up.default_budget_range,
                       up.dietary_preference,
                       (
                           SELECT us.location_lat
                           FROM user_sessions us
                           WHERE us.user_id = u.id
                           ORDER BY us.login_at DESC
                           LIMIT 1
                       ) AS last_location_lat,
                       (
                           SELECT us.location_lon
                           FROM user_sessions us
                           WHERE us.user_id = u.id
                           ORDER BY us.login_at DESC
                           LIMIT 1
                       ) AS last_location_lon,
                       (
                           SELECT us.location_source
                           FROM user_sessions us
                           WHERE us.user_id = u.id
                           ORDER BY us.login_at DESC
                           LIMIT 1
                       ) AS last_location_source,
                       (
                           SELECT COUNT(*)
                           FROM recommendation_history rh
                           WHERE rh.user_id = u.id
                       ) AS total_searches
                FROM app_users u
                LEFT JOIN user_profiles up ON up.user_id = u.id
                ORDER BY u.created_at DESC
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<UserSummary> users = new ArrayList<>();
            while (rs.next()) {
                users.add(new UserSummary(
                        rs.getLong("id"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getInt("is_active") == 1,
                        rs.getInt("onboarding_completed") == 1,
                        rs.getInt("login_count"),
                        rs.getString("last_login_at"),
                        rs.getString("created_at"),
                        rs.getString("preferred_cafe_type"),
                        rs.getString("default_budget_range"),
                        rs.getString("dietary_preference"),
                        getNullableDouble(rs, "last_location_lat"),
                        getNullableDouble(rs, "last_location_lon"),
                        rs.getString("last_location_source"),
                        rs.getInt("total_searches")
                ));
            }
            return users;
        }
    }

    private AppUser requireUserById(Connection connection, long userId) throws SQLException {
        final String sql = """
                SELECT id, user_key, display_name, email, role, is_active, onboarding_completed,
                       last_login_at, login_count, created_at, updated_at
                FROM app_users
                WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAppUser(rs);
                }
                throw new SQLException("User not found for id=" + userId);
            }
        }
    }

    private AuthSession requireSessionById(Connection connection, long sessionId) throws SQLException {
        final String sql = """
                SELECT id, user_id, session_token, login_at, logout_at, location_lat, location_lon, location_source
                FROM user_sessions
                WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapSession(rs);
                }
                throw new SQLException("Session not found for id=" + sessionId);
            }
        }
    }

    private static void validateEmailPassword(String email, String password) {
        if (normalizeEmail(email).isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
    }

    private static String normalizeEmail(String email) {
        return safe(email).toLowerCase(Locale.ROOT);
    }

    private static String buildUserKey(String name, String email) {
        String base = safe(name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = email.split("@")[0].replaceAll("[^a-z0-9]+", "-");
        }
        return base + "-" + Integer.toHexString(email.hashCode());
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeLocationSource(String source) {
        String normalized = safe(source).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "live", "address" -> normalized;
            default -> "unknown";
        };
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private AppUser mapAppUser(ResultSet rs) throws SQLException {
        return new AppUser(
                rs.getLong("id"),
                rs.getString("user_key"),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getInt("is_active") == 1,
                rs.getInt("onboarding_completed") == 1,
                rs.getString("last_login_at"),
                rs.getInt("login_count"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }

    private AuthSession mapSession(ResultSet rs) throws SQLException {
        return new AuthSession(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("session_token"),
                rs.getString("login_at"),
                rs.getString("logout_at"),
                getNullableDouble(rs, "location_lat"),
                getNullableDouble(rs, "location_lon"),
                rs.getString("location_source")
        );
    }
}
