package spatial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import model.Cafe;

public class KDTree {
    private static class KDNode {
        Cafe cafe;
        KDNode left;
        KDNode right;

        KDNode(Cafe cafe) {
            this.cafe = cafe;
        }
    }

    private KDNode root;

    public KDTree(List<Cafe> cafes) {
        List<Cafe> copy = new ArrayList<>(cafes);
        this.root = build(copy, 0);
    }

    private KDNode build(List<Cafe> cafes, int depth) {
        if (cafes.isEmpty()) {
            return null;
        }

        int axis = depth % 2;
        cafes.sort(axis == 0
                ? Comparator.comparingDouble(Cafe::getLatitude)
                : Comparator.comparingDouble(Cafe::getLongitude));

        int mid = cafes.size() / 2;
        KDNode node = new KDNode(cafes.get(mid));
        node.left = build(new ArrayList<>(cafes.subList(0, mid)), depth + 1);
        node.right = build(new ArrayList<>(cafes.subList(mid + 1, cafes.size())), depth + 1);
        return node;
    }

    public List<Cafe> rangeSearch(double centerLat, double centerLon, double radiusKm) {
        double latDelta = radiusKm / 111.0;
        double lonDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(centerLat)) + 1e-9);
        double minLat = centerLat - latDelta;
        double maxLat = centerLat + latDelta;
        double minLon = centerLon - lonDelta;
        double maxLon = centerLon + lonDelta;

        List<Cafe> result = new ArrayList<>();
        rangeSearch(root, 0, minLat, maxLat, minLon, maxLon, result);
        return result;
    }

    private void rangeSearch(KDNode node,
                             int depth,
                             double minLat,
                             double maxLat,
                             double minLon,
                             double maxLon,
                             List<Cafe> out) {
        if (node == null) {
            return;
        }

        Cafe cafe = node.cafe;
        if (cafe.getLatitude() >= minLat && cafe.getLatitude() <= maxLat
                && cafe.getLongitude() >= minLon && cafe.getLongitude() <= maxLon) {
            out.add(cafe);
        }

        int axis = depth % 2;
        if (axis == 0) {
            if (minLat <= cafe.getLatitude()) {
                rangeSearch(node.left, depth + 1, minLat, maxLat, minLon, maxLon, out);
            }
            if (maxLat >= cafe.getLatitude()) {
                rangeSearch(node.right, depth + 1, minLat, maxLat, minLon, maxLon, out);
            }
        } else {
            if (minLon <= cafe.getLongitude()) {
                rangeSearch(node.left, depth + 1, minLat, maxLat, minLon, maxLon, out);
            }
            if (maxLon >= cafe.getLongitude()) {
                rangeSearch(node.right, depth + 1, minLat, maxLat, minLon, maxLon, out);
            }
        }
    }
}
