package db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class AdminSeeder {
    public static final String SEEDED_ADMIN_NAME = "System Administrator";
    public static final String SEEDED_ADMIN_EMAIL = "admin@cafevibefinder.local";
    public static final String SEEDED_ADMIN_PASSWORD = "Admin@123";
    public static final String SEEDED_ADMIN_ROLE = "ADMIN";
    public static final String SEEDED_ADMIN_USER_KEY = "admin-root";

    private AdminSeeder() {
    }

    public static void ensureSeededAdmin(Connection connection) throws SQLException {
        if (hasAnyAdmin(connection)) {
            return;
        }
        final String sql = """
                INSERT INTO app_users(
                    user_key, display_name, email, password_hash, role, is_active, onboarding_completed, login_count
                )
                VALUES (?, ?, ?, ?, 'ADMIN', 1, 0, 0)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, SEEDED_ADMIN_USER_KEY);
            ps.setString(2, SEEDED_ADMIN_NAME);
            ps.setString(3, SEEDED_ADMIN_EMAIL);
            ps.setString(4, PasswordHasher.hashPassword(SEEDED_ADMIN_PASSWORD));
            ps.executeUpdate();
        }
    }

    private static boolean hasAnyAdmin(Connection connection) throws SQLException {
        final String sql = "SELECT 1 FROM app_users WHERE role = 'ADMIN' LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }
}
