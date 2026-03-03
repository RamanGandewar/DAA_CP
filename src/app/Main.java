package app;

import web.CafeHttpServer;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        try {
            String geocodedPath = "data/micuppa cafe dataset.geocoded.xlsx";
            String sourcePath = Files.exists(Path.of(geocodedPath))
                    ? geocodedPath
                    : "data/micuppa cafe dataset.xlsx";
            CafeHttpServer server = CafeHttpServer.buildDefault(port, sourcePath);
            server.start();
        } catch (Exception e) {
            System.err.println("Failed to start application: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
