package data;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import model.Cafe;

public class DataLoader {
    public List<Cafe> loadFromCsv(String filePath) throws IOException {
        IOException lastError = null;
        Charset[] candidates = new Charset[] { StandardCharsets.UTF_8, Charset.forName("windows-1252") };
        for (Charset cs : candidates) {
            try {
                return loadFromCsv(filePath, cs);
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw lastError == null ? new IOException("Unable to read CSV: " + filePath) : lastError;
    }

    private List<Cafe> loadFromCsv(String filePath, Charset charset) throws IOException {
        List<Cafe> cafes = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Path.of(filePath), charset)) {
            String line = br.readLine();
            if (line == null) {
                return cafes;
            }

            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] t = line.split(",", -1);
                if (t.length < 13) {
                    continue;
                }

                String id = t[0].trim();
                String name = t[1].trim();
                double lat = Double.parseDouble(t[2].trim());
                double lon = Double.parseDouble(t[3].trim());
                Set<String> cuisines = parseCuisines(t[4]);
                double avgPrice = Double.parseDouble(t[5].trim());
                double rating = Double.parseDouble(t[6].trim());
                boolean veg = Boolean.parseBoolean(t[7].trim());
                boolean nonVeg = Boolean.parseBoolean(t[8].trim());
                boolean vegan = Boolean.parseBoolean(t[9].trim());
                String hours = t[10].trim();
                String address = t[11].trim();
                String contact = t[12].trim();

                Cafe cafe = new Cafe(id, name, lat, lon, cuisines, avgPrice, rating, veg, nonVeg, vegan, hours, address, contact);
                cafes.add(cafe);
            }
        }
        return cafes;
    }

    public GlobalStats computeGlobalStats(List<Cafe> cafes) {
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;

        for (Cafe cafe : cafes) {
            minPrice = Math.min(minPrice, cafe.getAvgPrice());
            maxPrice = Math.max(maxPrice, cafe.getAvgPrice());
        }

        if (minPrice == Double.MAX_VALUE) {
            minPrice = 0;
        }
        if (maxPrice == Double.MIN_VALUE) {
            maxPrice = 1;
        }

        return new GlobalStats(minPrice, maxPrice);
    }

    private Set<String> parseCuisines(String raw) {
        Set<String> cuisines = new HashSet<>();
        String[] parts = raw.split("\\|");
        for (String p : parts) {
            String c = p.trim().toLowerCase();
            if (!c.isEmpty()) {
                cuisines.add(c);
            }
        }
        return cuisines;
    }
}
