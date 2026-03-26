package model;

import java.util.List;

public class AdminOverview {
    private final int totalUsers;
    private final int totalAdmins;
    private final int totalLogins;
    private final int totalSearches;
    private final int onboardingCompletedCount;
    private final List<UserSummary> users;

    public AdminOverview(int totalUsers,
                         int totalAdmins,
                         int totalLogins,
                         int totalSearches,
                         int onboardingCompletedCount,
                         List<UserSummary> users) {
        this.totalUsers = totalUsers;
        this.totalAdmins = totalAdmins;
        this.totalLogins = totalLogins;
        this.totalSearches = totalSearches;
        this.onboardingCompletedCount = onboardingCompletedCount;
        this.users = users;
    }

    public int getTotalUsers() { return totalUsers; }
    public int getTotalAdmins() { return totalAdmins; }
    public int getTotalLogins() { return totalLogins; }
    public int getTotalSearches() { return totalSearches; }
    public int getOnboardingCompletedCount() { return onboardingCompletedCount; }
    public List<UserSummary> getUsers() { return users; }
}
