package model;

public final class Hypothesis {
    private final long id;
    private final String caseId;
    private final String hypothesisCode;
    private final String title;
    private final String description;
    private final String status;
    private final String createdBy;
    private final String createdAt;
    private final String updatedAt;
    private final int contradictionCount;
    private final int supportCount;
    private final int contextCount;
    private final int unassessedCount;

    public Hypothesis(long id, String caseId, String hypothesisCode, String title, String description,
                      String status, String createdBy, String createdAt, String updatedAt,
                      int contradictionCount, int supportCount, int contextCount, int unassessedCount) {
        this.id = id;
        this.caseId = caseId;
        this.hypothesisCode = hypothesisCode;
        this.title = title;
        this.description = description;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.contradictionCount = contradictionCount;
        this.supportCount = supportCount;
        this.contextCount = contextCount;
        this.unassessedCount = unassessedCount;
    }

    public long getId() { return id; }
    public String getCaseId() { return caseId; }
    public String getHypothesisCode() { return hypothesisCode; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public int getContradictionCount() { return contradictionCount; }
    public int getSupportCount() { return supportCount; }
    public int getContextCount() { return contextCount; }
    public int getUnassessedCount() { return unassessedCount; }
}
