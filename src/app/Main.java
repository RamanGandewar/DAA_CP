package app;

import db.DatabaseManager;
import db.DatabaseStatus;
import web.CafeHttpServer;

public class Main {
    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        try {
            DatabaseStatus databaseStatus = DatabaseManager.initialize();
            System.out.println("Database status: " + (databaseStatus.isEnabled() ? "enabled" : "disabled"));
            System.out.println("Database URL: " + databaseStatus.getJdbcUrl());
            System.out.println("Database message: " + databaseStatus.getMessage());
            CafeHttpServer server = CafeHttpServer.buildDefault(port);
            server.start();
        } catch (Exception e) {
            System.err.println("Failed to start application: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
