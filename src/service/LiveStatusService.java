package service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LiveStatusService {
    private final ConcurrentHashMap<String, AtomicInteger> easyVotes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> standingVotes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> tableShare = new ConcurrentHashMap<>();

    public void voteSeatStatus(String cafeId, String status) {
        if ("easy".equalsIgnoreCase(status)) {
            easyVotes.computeIfAbsent(cafeId, k -> new AtomicInteger()).incrementAndGet();
        } else if ("standing".equalsIgnoreCase(status)) {
            standingVotes.computeIfAbsent(cafeId, k -> new AtomicInteger()).incrementAndGet();
        } else {
            throw new IllegalArgumentException("status must be 'easy' or 'standing'");
        }
    }

    public void setTableShare(String cafeId, boolean available) {
        tableShare.put(cafeId, available);
    }

    public LiveStatus getStatus(String cafeId) {
        int easy = easyVotes.getOrDefault(cafeId, new AtomicInteger()).get();
        int standing = standingVotes.getOrDefault(cafeId, new AtomicInteger()).get();
        boolean share = tableShare.getOrDefault(cafeId, false);
        return new LiveStatus(easy, standing, share);
    }
}
