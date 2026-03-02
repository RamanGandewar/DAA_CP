package rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import model.Recommendation;

public class TopKSelector {
    public List<Recommendation> selectTopK(List<Recommendation> scored, int k) {
        if (k <= 0) {
            return List.of();
        }

        PriorityQueue<Recommendation> heap = new PriorityQueue<>(
                Comparator.comparingDouble(Recommendation::getScore).reversed()
                          .thenComparing(Comparator.comparingDouble((Recommendation r) -> r.getCafe().getRating()))
                          .thenComparing(Comparator.comparingDouble(Recommendation::getDistanceKm).reversed())
        );

        for (Recommendation rec : scored) {
            if (heap.size() < k) {
                heap.offer(rec);
            } else if (isBetter(rec, heap.peek())) {
                heap.poll();
                heap.offer(rec);
            }
        }

        List<Recommendation> result = new ArrayList<>(heap);
        result.sort(Comparator.comparingDouble(Recommendation::getScore)
                .thenComparing(Comparator.comparingDouble((Recommendation r) -> r.getCafe().getRating()).reversed())
                .thenComparing(Comparator.comparingDouble(Recommendation::getDistanceKm)));
        return result;
    }

    private boolean isBetter(Recommendation a, Recommendation b) {
        if (a.getScore() != b.getScore()) {
            return a.getScore() < b.getScore();
        }
        if (a.getCafe().getRating() != b.getCafe().getRating()) {
            return a.getCafe().getRating() > b.getCafe().getRating();
        }
        return a.getDistanceKm() < b.getDistanceKm();
    }
}
