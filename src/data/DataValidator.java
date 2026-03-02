package data;

import java.util.List;
import model.Cafe;

public final class DataValidator {
    private DataValidator() {}

    public static void validate(List<Cafe> cafes) {
        if (cafes == null || cafes.isEmpty()) {
            throw new IllegalArgumentException("No cafes loaded. Check CSV path/content.");
        }
    }
}
