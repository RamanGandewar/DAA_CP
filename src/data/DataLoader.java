package data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import model.Cafe;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DataLoader {
    private static final double BASE_LAT = 18.5204;
    private static final double BASE_LON = 73.8567;

    public List<Cafe> loadFromFile(String filePath) throws IOException {
        String normalized = filePath.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".xlsx")) {
            return loadFromXlsx(filePath);
        }
        return loadFromCsv(filePath);
    }

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

    private List<Cafe> loadFromXlsx(String filePath) throws IOException {
        List<Cafe> cafes = new ArrayList<>();
        Map<String, double[]> locationCenters = new HashMap<>();

        try (ZipFile zip = new ZipFile(filePath)) {
            List<String> sharedStrings = parseSharedStrings(zip);
            List<Map<Integer, String>> rows = parseSheetRows(zip, sharedStrings);
            if (rows.isEmpty()) {
                return cafes;
            }

            Map<Integer, String> headers = rows.get(0);
            int rowNumber = 1;
            for (int i = 1; i < rows.size(); i++) {
                rowNumber++;
                Map<Integer, String> row = rows.get(i);
                if (row.isEmpty()) {
                    continue;
                }

                String name = getByHeader(headers, row, "Name");
                String location = cleanLocation(getByHeader(headers, row, "Location"));
                if (name.isBlank() || location.isBlank()) {
                    continue;
                }

                String food = getByHeader(headers, row, "Food");
                String ambience = getByHeader(headers, row, "Ambience");
                String event = getByHeader(headers, row, "Event");
                String wifi = getByHeader(headers, row, "Wi-Fi");
                String time = getByHeader(headers, row, "Time");
                String peopleRange = getByHeader(headers, row, "No. of People");
                String speciality = getByHeader(headers, row, "Speciality");
                String maxCapacity = getByHeader(headers, row, "MaxCapacity");
                String ambienceType = getByHeader(headers, row, "Ambiencetype");
                String latitudeRaw = firstNonBlank(
                        getByHeader(headers, row, "Latitude"),
                        getByHeader(headers, row, "Lat")
                );
                String longitudeRaw = firstNonBlank(
                        getByHeader(headers, row, "Longitude"),
                        getByHeader(headers, row, "Lon"),
                        getByHeader(headers, row, "Lng")
                );

                String id = buildId(name, location, rowNumber);
                Double latitude = parseOptionalDouble(latitudeRaw);
                Double longitude = parseOptionalDouble(longitudeRaw);
                double[] point;
                if (latitude != null && longitude != null) {
                    point = new double[] { latitude, longitude };
                } else {
                    point = deriveCoordinates(locationCenters, name, location, rowNumber);
                }
                Set<String> cuisines = parseCuisines(food + "|" + speciality);
                double avgPrice = deriveAveragePrice(maxCapacity, peopleRange, name);
                double rating = deriveRating(wifi, event, ambience, ambienceType, speciality, name);
                boolean vegan = hasAny(food, "vegan", "plant");
                boolean nonVeg = hasAny(food, "chicken", "mutton", "seafood", "fish", "meat", "kebab", "egg", "biryani");
                boolean veg = vegan || hasAny(food, "veg", "vegetarian", "coffee", "chai", "tea", "dessert", "bakery", "italian", "south indian", "north indian", "continental");
                if (!veg && !nonVeg) {
                    veg = true;
                    nonVeg = true;
                }

                Cafe cafe = new Cafe(
                        id,
                        name,
                        point[0],
                        point[1],
                        cuisines,
                        avgPrice,
                        rating,
                        veg,
                        nonVeg,
                        vegan,
                        time,
                        location,
                        ""
                );
                cafes.add(cafe);
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse XLSX: " + filePath, e);
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
        String[] parts = raw.split("\\||,|/|&|(?i)\\band\\b");
        for (String p : parts) {
            String c = p.trim().toLowerCase(Locale.ROOT);
            if (!c.isEmpty()) {
                cuisines.add(c);
            }
        }
        return cuisines;
    }

    private List<String> parseSharedStrings(ZipFile zip) throws Exception {
        ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        if (entry == null) {
            return List.of();
        }

        Document doc = parseXml(zip, entry);
        NodeList sis = doc.getElementsByTagName("si");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < sis.getLength(); i++) {
            NodeList texts = ((Element) sis.item(i)).getElementsByTagName("t");
            StringBuilder b = new StringBuilder();
            for (int j = 0; j < texts.getLength(); j++) {
                b.append(texts.item(j).getTextContent());
            }
            out.add(b.toString());
        }
        return out;
    }

    private List<Map<Integer, String>> parseSheetRows(ZipFile zip, List<String> sharedStrings) throws Exception {
        ZipEntry sheet = zip.getEntry("xl/worksheets/sheet1.xml");
        if (sheet == null) {
            return List.of();
        }

        Document doc = parseXml(zip, sheet);
        NodeList rowNodes = doc.getElementsByTagName("row");
        List<Map<Integer, String>> rows = new ArrayList<>();
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element row = (Element) rowNodes.item(i);
            NodeList cells = row.getElementsByTagName("c");
            Map<Integer, String> data = new HashMap<>();

            for (int j = 0; j < cells.getLength(); j++) {
                Element cell = (Element) cells.item(j);
                String ref = cell.getAttribute("r");
                int col = columnIndex(ref);
                String type = cell.getAttribute("t");

                String value = "";
                if ("inlineStr".equals(type)) {
                    NodeList inline = cell.getElementsByTagName("t");
                    if (inline.getLength() > 0) {
                        value = inline.item(0).getTextContent();
                    }
                } else {
                    NodeList v = cell.getElementsByTagName("v");
                    if (v.getLength() > 0) {
                        value = v.item(0).getTextContent();
                        if ("s".equals(type)) {
                            int idx = parseInt(value, -1);
                            if (idx >= 0 && idx < sharedStrings.size()) {
                                value = sharedStrings.get(idx);
                            }
                        }
                    }
                }
                data.put(col, value == null ? "" : value.trim());
            }
            rows.add(data);
        }
        return rows;
    }

    private Document parseXml(ZipFile zip, ZipEntry entry) throws Exception {
        try (InputStream is = zip.getInputStream(entry)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            return factory.newDocumentBuilder().parse(is);
        }
    }

    private String getByHeader(Map<Integer, String> headers, Map<Integer, String> row, String headerName) {
        for (Map.Entry<Integer, String> h : headers.entrySet()) {
            if (headerName.equalsIgnoreCase(h.getValue())) {
                return row.getOrDefault(h.getKey(), "").trim();
            }
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String cleanLocation(String location) {
        return location.replaceAll("(?i)\\bfrom\\s+from\\b", "from").trim();
    }

    private String buildId(String name, String location, int rowNumber) {
        String base = (name + "-" + location).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        base = base.replaceAll("(^-+|-+$)", "");
        return base + "-" + rowNumber;
    }

    private double[] deriveCoordinates(Map<String, double[]> locationCenters, String name, String location, int rowNumber) {
        String locationKey = location.toLowerCase(Locale.ROOT);
        double[] center = locationCenters.computeIfAbsent(locationKey, key -> {
            int h = Math.abs(key.hashCode());
            double latShift = ((h % 4001) - 2000) / 100000.0;
            double lonShift = (((h / 4019) % 4001) - 2000) / 100000.0;
            return new double[] { BASE_LAT + latShift, BASE_LON + lonShift };
        });

        int cafeHash = Math.abs((name + "|" + rowNumber).hashCode());
        double latJitter = ((cafeHash % 601) - 300) / 100000.0;
        double lonJitter = (((cafeHash / 613) % 601) - 300) / 100000.0;
        return new double[] { center[0] + latJitter, center[1] + lonJitter };
    }

    private double deriveAveragePrice(String maxCapacity, String peopleRange, String name) {
        String c = maxCapacity == null ? "" : maxCapacity.trim().toLowerCase(Locale.ROOT);
        double base;
        if ("small".equals(c)) {
            base = 250;
        } else if ("medium".equals(c)) {
            base = 420;
        } else if ("large".equals(c)) {
            base = 650;
        } else {
            base = 360;
        }

        int upper = parsePeopleUpperBound(peopleRange);
        if (upper > 0) {
            base += upper * 1.7;
        } else {
            base += (Math.abs(name.hashCode()) % 120);
        }
        return clamp(base, 140, 1200);
    }

    private int parsePeopleUpperBound(String peopleRange) {
        if (peopleRange == null || peopleRange.isBlank()) {
            return -1;
        }
        String raw = peopleRange.trim();
        if (raw.contains("-")) {
            String[] p = raw.split("-");
            if (p.length >= 2) {
                return parseInt(p[1].trim(), -1);
            }
        }
        return parseInt(raw, -1);
    }

    private double deriveRating(String wifi, String event, String ambience, String ambienceType, String speciality, String name) {
        double score = 3.1;
        if ("yes".equalsIgnoreCase(wifi)) {
            score += 0.45;
        }
        if (event != null && !event.isBlank() && !"not specified".equalsIgnoreCase(event)) {
            score += 0.3;
        }
        if (ambience != null && !ambience.isBlank()) {
            score += Math.min(0.5, ambience.split("[,/& ]+").length * 0.05);
        }
        if (ambienceType != null && !ambienceType.isBlank()) {
            score += Math.min(0.4, ambienceType.split("[/,& ]+").length * 0.04);
        }
        if (speciality != null && !speciality.isBlank()) {
            score += 0.15;
        }
        int h = Math.abs(name.hashCode());
        score += ((h % 21) - 10) / 100.0;
        return clamp(score, 2.9, 4.9);
    }

    private boolean hasAny(String text, String... terms) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String t : terms) {
            if (normalized.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private Double parseOptionalDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int columnIndex(String cellRef) {
        if (cellRef == null || cellRef.isBlank()) {
            return -1;
        }
        int index = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char ch = cellRef.charAt(i);
            if (Character.isLetter(ch)) {
                index = index * 26 + (Character.toUpperCase(ch) - 'A' + 1);
            } else {
                break;
            }
        }
        return index - 1;
    }
}
