package model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single crime case, including its severity, status,
 * assigned officer, linked suspects, associated evidence, and a
 * computed priority score.
 */
public class CaseRecord {

    private String caseId;
    private String title;
    private String description;
    private Severity severity;
    private CaseStatus status;
    private LocalDate dateOpened;

    private Officer assignedOfficer;
    private List<Suspect> linkedSuspects;
    private List<Evidence> evidenceList;

    public CaseRecord(String caseId, String title, String description, Severity severity) {
        this.caseId = caseId;
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.status = CaseStatus.OPEN;
        this.dateOpened = LocalDate.now();
        this.linkedSuspects = new ArrayList<>();
        this.evidenceList = new ArrayList<>();
    }

    // ----- Domain behaviour -----

    public void assignOfficer(Officer officer) {
        this.assignedOfficer = officer;
        if (officer != null) {
            officer.incrementActiveCaseCount();
        }
    }

    public void linkSuspect(Suspect suspect) {
        linkedSuspects.add(suspect);
    }

    public void addEvidence(Evidence evidence) {
        evidenceList.add(evidence);
    }

    /**
     * Computes a priority score from the case's severity weight and
     * the number of days it has been open. Higher = more urgent.
     * Formula: (severityWeight * 10) + daysOpen
     */
    public int calculatePriorityScore() {
        long daysOpen = ChronoUnit.DAYS.between(dateOpened, LocalDate.now());
        return (severity.getWeight() * 10) + (int) daysOpen;
    }

    // ----- Getters and Setters -----

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
    }

    public LocalDate getDateOpened() {
        return dateOpened;
    }

    public void setDateOpened(LocalDate dateOpened) {
        this.dateOpened = dateOpened;
    }

    public Officer getAssignedOfficer() {
        return assignedOfficer;
    }

    public List<Suspect> getLinkedSuspects() {
        return linkedSuspects;
    }

    public List<Evidence> getEvidenceList() {
        return evidenceList;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CaseRecord{")
          .append("caseId='").append(caseId).append('\'')
          .append(", title='").append(title).append('\'')
          .append(", severity=").append(severity)
          .append(", status=").append(status)
          .append(", dateOpened=").append(dateOpened)
          .append(", priorityScore=").append(calculatePriorityScore())
          .append(", assignedOfficer=")
          .append(assignedOfficer != null ? assignedOfficer.getName() : "UNASSIGNED")
          .append(", linkedSuspects=").append(linkedSuspects.size())
          .append(", evidenceCount=").append(evidenceList.size())
          .append('}');
        return sb.toString();
    }
}
