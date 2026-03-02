package model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Cafe {
    private final String id;
    private final String name;
    private final double latitude;
    private final double longitude;
    private final Set<String> cuisines;
    private final double avgPrice;
    private final double rating;
    private final boolean veg;
    private final boolean nonVeg;
    private final boolean vegan;
    private final String operatingHours;
    private final String address;
    private final String contact;

    public Cafe(String id,
                String name,
                double latitude,
                double longitude,
                Set<String> cuisines,
                double avgPrice,
                double rating,
                boolean veg,
                boolean nonVeg,
                boolean vegan,
                String operatingHours,
                String address,
                String contact) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.cuisines = new HashSet<>(cuisines);
        this.avgPrice = avgPrice;
        this.rating = rating;
        this.veg = veg;
        this.nonVeg = nonVeg;
        this.vegan = vegan;
        this.operatingHours = operatingHours;
        this.address = address;
        this.contact = contact;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Set<String> getCuisines() { return Collections.unmodifiableSet(cuisines); }
    public double getAvgPrice() { return avgPrice; }
    public double getRating() { return rating; }
    public boolean isVeg() { return veg; }
    public boolean isNonVeg() { return nonVeg; }
    public boolean isVegan() { return vegan; }
    public String getOperatingHours() { return operatingHours; }
    public String getAddress() { return address; }
    public String getContact() { return contact; }
}
