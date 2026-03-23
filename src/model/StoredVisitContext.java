package model;

public class StoredVisitContext {
    private final long id;
    private final long userId;
    private final VisitContext visitContext;
    private final boolean active;
    private final String createdAt;

    public StoredVisitContext(long id,
                              long userId,
                              VisitContext visitContext,
                              boolean active,
                              String createdAt) {
        this.id = id;
        this.userId = userId;
        this.visitContext = visitContext == null ? VisitContext.empty() : visitContext;
        this.active = active;
        this.createdAt = createdAt == null ? "" : createdAt;
    }

    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public VisitContext getVisitContext() {
        return visitContext;
    }

    public boolean isActive() {
        return active;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
