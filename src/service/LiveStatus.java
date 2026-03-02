package service;

public class LiveStatus {
    private final int easySeatVotes;
    private final int standingVotes;
    private final boolean tableShareAvailable;

    public LiveStatus(int easySeatVotes, int standingVotes, boolean tableShareAvailable) {
        this.easySeatVotes = easySeatVotes;
        this.standingVotes = standingVotes;
        this.tableShareAvailable = tableShareAvailable;
    }

    public int getEasySeatVotes() { return easySeatVotes; }
    public int getStandingVotes() { return standingVotes; }
    public boolean isTableShareAvailable() { return tableShareAvailable; }
}
