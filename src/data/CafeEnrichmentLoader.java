package data;

import model.CafeEnrichment;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CafeEnrichmentLoader {
    public Map<String, CafeEnrichment> loadForDataset(String datasetPath) {
        Map<String, CafeEnrichment> enrichments = new HashMap<>();
        for (Path candidate : candidatePaths(datasetPath)) {
            if (!Files.exists(candidate)) {
                continue;
            }
            try {
                return loadTsv(candidate);
            } catch (IOException ignored) {
                System.err.println("Warning: unable to load cafe enrichment file: " + candidate);
            }
        }
        return enrichments;
    }

    private Map<String, CafeEnrichment> loadTsv(Path path) throws IOException {
        Map<String, CafeEnrichment> out = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                return out;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 9) {
                    continue;
                }

                String cafeId = parts[0].trim();
                if (cafeId.isBlank()) {
                    continue;
                }

                Set<String> occasionTags = parseTags(parts[1]);
                int hangout = parseScore(parts[2], 6);
                int date = parseScore(parts[3], 5);
                int work = parseScore(parts[4], 5);
                int meeting = parseScore(parts[5], 5);
                int quick = parseScore(parts[6], 5);
                int privacy = parseScore(parts[7], 5);
                int aesthetic = parseScore(parts[8], 5);
                String summary = parts.length > 9 ? parts[9].trim() : "";

                out.put(cafeId, new CafeEnrichment(
                        occasionTags,
                        hangout,
                        date,
                        work,
                        meeting,
                        quick,
                        privacy,
                        aesthetic,
                        summary
                ));
            }
        }
        return out;
    }

    private Path[] candidatePaths(String datasetPath) {
        Path dataset = Path.of(datasetPath);
        String fileName = dataset.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot >= 0 ? fileName.substring(0, dot) : fileName;
        Path sibling = dataset.resolveSibling(baseName + ".enrichment.tsv");
        Path dataFolder = dataset.getParent() == null ? Path.of("data") : dataset.getParent();
        Path fallback = dataFolder.resolve("cafe_ai_enrichment.tsv");
        return new Path[] { sibling, fallback };
    }

    private Set<String> parseTags(String raw) {
        Set<String> tags = new HashSet<>();
        if (raw == null || raw.isBlank()) {
            return tags;
        }
        for (String part : raw.split(",")) {
            String value = part.trim().toLowerCase(Locale.ROOT);
            if (!value.isBlank()) {
                tags.add(value);
            }
        }
        return tags;
    }

    private int parseScore(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
