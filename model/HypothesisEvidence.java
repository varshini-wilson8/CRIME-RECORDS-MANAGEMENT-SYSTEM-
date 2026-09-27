package model;

public final class HypothesisEvidence {
    private final long id;
    private final long hypothesisId;
    private final String evidenceId;
    private final String relationshipType; // SUPPORTS, CONTRADICTS, CONTEXT, UNASSESSED
    private final String notes;
    private final String createdBy;
    private final String updatedAt;

    public HypothesisEvidence(long id, long hypothesisId, String evidenceId, String relationshipType,
                              String notes, String createdBy, String updatedAt) {
        this.id = id;
        this.hypothesisId = hypothesisId;
        this.evidenceId = evidenceId;
        this.relationshipType = (relationshipType != null && !relationshipType.isBlank()) ? relationshipType : "UNASSESSED";
        this.notes = notes != null ? notes : "";
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
    }

    public long getId() { return id; }
    public long getHypothesisId() { return hypothesisId; }
    public String getEvidenceId() { return evidenceId; }
    public String getRelationshipType() { return relationshipType; }
    public String getNotes() { return notes; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedAt() { return updatedAt; }
}
