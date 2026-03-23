package db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseManager {
    private static final String DRIVER_CLASS = "org.sqlite.JDBC";
    private static final Path DB_PATH = Paths.get("data", "cafe_recommendation.db");
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH.toString().replace('\\', '/');

    private static volatile DatabaseStatus status = new DatabaseStatus(false, JDBC_URL, "Database not initialized.");

    private DatabaseManager() {
    }

    public static synchronized DatabaseStatus initialize() {
        try {
            Files.createDirectories(DB_PATH.getParent());
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            status = new DatabaseStatus(
                    false,
                    JDBC_URL,
                    "SQLite JDBC driver not found. Place sqlite-jdbc jar in lib/ to enable onboarding storage."
            );
            return status;
        } catch (Exception e) {
            status = new DatabaseStatus(false, JDBC_URL, "Failed to prepare database path: " + e.getMessage());
            return status;
        }

        try (Connection connection = getConnection()) {
            SchemaInitializer.initialize(connection);
            status = new DatabaseStatus(true, JDBC_URL, "SQLite schema initialized successfully.");
        } catch (Exception e) {
            status = new DatabaseStatus(false, JDBC_URL, "SQLite initialization failed: " + e.getMessage());
        }
        return status;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL);
    }

    public static DatabaseStatus getStatus() {
        return status;
    }
}
