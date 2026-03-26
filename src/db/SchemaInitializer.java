package db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class SchemaInitializer {
    private SchemaInitializer() {
    }

    public static void initialize(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS app_users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_key TEXT NOT NULL UNIQUE,
                        display_name TEXT NOT NULL DEFAULT '',
                        onboarding_completed INTEGER NOT NULL DEFAULT 0 CHECK (onboarding_completed IN (0, 1)),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            ensureAppUserColumns(connection);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS user_profiles (
                        user_id INTEGER PRIMARY KEY,
                        full_name TEXT NOT NULL,
                        age_group TEXT NOT NULL CHECK (age_group IN ('student', 'working professional', 'other')),
                        occupation TEXT NOT NULL,
                        default_budget_range TEXT NOT NULL CHECK (default_budget_range IN ('low', 'medium', 'high')),
                        preferred_cafe_type TEXT NOT NULL CHECK (
                            preferred_cafe_type IN ('quiet / work-friendly', 'social / hangout', 'premium / aesthetic')
                        ),
                        preferred_distance_km INTEGER NOT NULL CHECK (preferred_distance_km IN (1, 3, 5, 10)),
                        dietary_preference TEXT NOT NULL CHECK (
                            dietary_preference IN ('vegetarian', 'non-vegetarian', 'vegan', 'no preference')
                        ),
                        dominant_profile_tag TEXT NOT NULL CHECK (
                            dominant_profile_tag IN ('study_work', 'social_hangout', 'date_aesthetic', 'quick_coffee')
                        ),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS user_social_preferences (
                        user_id INTEGER PRIMARY KEY,
                        usually_visit_with TEXT NOT NULL CHECK (
                            usually_visit_with IN ('alone', 'with friends', 'with colleagues', 'with partner')
                        ),
                        preferred_seating TEXT NOT NULL CHECK (
                            preferred_seating IN ('indoor', 'outdoor', 'doesn''t matter')
                        ),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS user_ambience_preferences (
                        user_id INTEGER PRIMARY KEY,
                        music_preference TEXT NOT NULL CHECK (
                            music_preference IN ('silent', 'light music', 'loud / party vibe')
                        ),
                        lighting_preference TEXT NOT NULL CHECK (
                            lighting_preference IN ('bright', 'cozy', 'aesthetic / dim')
                        ),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS visit_contexts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        purpose_of_visit TEXT NOT NULL CHECK (
                            purpose_of_visit IN ('work / study', 'casual hangout', 'date', 'coffee break', 'meeting')
                        ),
                        current_budget_range TEXT NOT NULL CHECK (current_budget_range IN ('low', 'medium', 'high')),
                        travel_distance_km INTEGER NOT NULL CHECK (travel_distance_km IN (1, 3, 5, 10)),
                        time_of_visit TEXT NOT NULL CHECK (
                            time_of_visit IN ('morning', 'afternoon', 'evening', 'late night')
                        ),
                        crowd_tolerance TEXT NOT NULL CHECK (
                            crowd_tolerance IN ('quiet', 'moderate', 'lively')
                        ),
                        is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS recommendation_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        visit_context_id INTEGER,
                        source TEXT NOT NULL CHECK (source IN ('csv', 'xlsx')),
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        radius_km REAL NOT NULL,
                        budget REAL NOT NULL,
                        top_k INTEGER NOT NULL,
                        result_count INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE,
                        FOREIGN KEY (visit_context_id) REFERENCES visit_contexts(id) ON DELETE SET NULL
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS recommendation_explanations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        recommendation_history_id INTEGER NOT NULL,
                        cafe_id TEXT NOT NULL,
                        rank_position INTEGER NOT NULL,
                        explanation_text TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (recommendation_history_id) REFERENCES recommendation_history(id) ON DELETE CASCADE
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS user_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        session_token TEXT,
                        login_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        logout_at TEXT,
                        location_lat REAL,
                        location_lon REAL,
                        location_source TEXT NOT NULL DEFAULT 'unknown' CHECK (
                            location_source IN ('live', 'address', 'unknown')
                        ),
                        FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_app_users_user_key
                    ON app_users(user_key)
                    """);

            stmt.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_app_users_email_unique
                    ON app_users(email)
                    WHERE email IS NOT NULL AND email <> ''
                    """);

            stmt.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_app_users_single_admin
                    ON app_users(role)
                    WHERE role = 'ADMIN'
                    """);

            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_visit_contexts_user_id
                    ON visit_contexts(user_id)
                    """);

            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_visit_contexts_user_id_active
                    ON visit_contexts(user_id, is_active, created_at DESC)
                    """);

            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_recommendation_history_user_id
                    ON recommendation_history(user_id, created_at DESC)
                    """);

            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_recommendation_explanations_history_id
                    ON recommendation_explanations(recommendation_history_id, rank_position)
                    """);

            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id
                    ON user_sessions(user_id, login_at DESC)
                    """);

            addColumnIfMissing(connection, "user_sessions", "session_token", "TEXT");

            stmt.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_user_sessions_token
                    ON user_sessions(session_token)
                    WHERE session_token IS NOT NULL AND session_token <> ''
                    """);

            stmt.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS trg_app_users_updated_at
                    AFTER UPDATE ON app_users
                    FOR EACH ROW
                    BEGIN
                        UPDATE app_users
                        SET updated_at = CURRENT_TIMESTAMP
                        WHERE id = OLD.id;
                    END
                    """);

            stmt.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS trg_user_profiles_updated_at
                    AFTER UPDATE ON user_profiles
                    FOR EACH ROW
                    BEGIN
                        UPDATE user_profiles
                        SET updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = OLD.user_id;
                    END
                    """);

            stmt.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS trg_user_social_preferences_updated_at
                    AFTER UPDATE ON user_social_preferences
                    FOR EACH ROW
                    BEGIN
                        UPDATE user_social_preferences
                        SET updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = OLD.user_id;
                    END
                    """);

            stmt.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS trg_user_ambience_preferences_updated_at
                    AFTER UPDATE ON user_ambience_preferences
                    FOR EACH ROW
                    BEGIN
                        UPDATE user_ambience_preferences
                        SET updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = OLD.user_id;
                    END
                    """);
        }

        AdminSeeder.ensureSeededAdmin(connection);
    }

    private static void ensureAppUserColumns(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "app_users", "email", "TEXT");
        addColumnIfMissing(connection, "app_users", "password_hash", "TEXT");
        addColumnIfMissing(connection, "app_users", "role", "TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN', 'GUEST'))");
        addColumnIfMissing(connection, "app_users", "is_active", "INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1))");
        addColumnIfMissing(connection, "app_users", "last_login_at", "TEXT");
        addColumnIfMissing(connection, "app_users", "login_count", "INTEGER NOT NULL DEFAULT 0");
    }

    private static void addColumnIfMissing(Connection connection, String tableName, String columnName, String columnSql) throws SQLException {
        if (hasColumn(connection, tableName, columnName)) {
            return;
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnSql);
        }
    }

    private static boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            if (columns.next()) {
                return true;
            }
        }

        try (PreparedStatement ps = connection.prepareStatement("PRAGMA table_info(" + tableName + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
