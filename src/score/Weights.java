package score;

public class Weights {
    private final double wDistance;
    private final double wPrice;
    private final double wRating;
    private final double wCuisine;

    public Weights(double wDistance, double wPrice, double wRating, double wCuisine) {
        double sum = wDistance + wPrice + wRating + wCuisine;
        if (Math.abs(sum - 1.0) > 1e-9) {
            throw new IllegalArgumentException("Weights must sum to 1.0");
        }
        this.wDistance = wDistance;
        this.wPrice = wPrice;
        this.wRating = wRating;
        this.wCuisine = wCuisine;
    }

    public static Weights defaults() {
        return new Weights(0.3, 0.3, 0.2, 0.2);
    }

    public double getWDistance() { return wDistance; }
    public double getWPrice() { return wPrice; }
    public double getWRating() { return wRating; }
    public double getWCuisine() { return wCuisine; }
}
