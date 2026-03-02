package data;

public class GlobalStats {
    private final double minPrice;
    private final double maxPrice;

    public GlobalStats(double minPrice, double maxPrice) {
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    public double getMinPrice() { return minPrice; }
    public double getMaxPrice() { return maxPrice; }
}
