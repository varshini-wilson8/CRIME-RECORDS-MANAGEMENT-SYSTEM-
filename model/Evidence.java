package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single piece of evidence linked to a crime case.
 * Maintains its full chain-of-custody trail as a list of EvidenceLog entries.
 */
public class Evidence {

    private String evidenceId;
    private String description;
    private LocalDate collectionDate;
    private String currentCustodian;
    private List<EvidenceLog> custodyHistory;

    public Evidence(String evidenceId, String description,
                     LocalDate collectionDate, String currentCustodian) {
        this.evidenceId = evidenceId;
        this.description = description;
        this.collectionDate = collectionDate;
        this.currentCustodian = currentCustodian;
        this.custodyHistory = new ArrayList<>();

        // The very first custody entry: collection itself.
        this.custodyHistory.add(
                new EvidenceLog("COLLECTION", currentCustodian, "Initial collection of evidence")
        );
    }

    /**
     * Transfers custody of this evidence to a new custodian and
     * automatically records a timestamped log entry.
     */
    public void transferCustody(String transferredTo, String remarks) {
        EvidenceLog log = new EvidenceLog(this.currentCustodian, transferredTo, remarks);
        this.custodyHistory.add(log);
        this.currentCustodian = transferredTo;
    }

    /**
     * Returns a read-only view of the custody trail so external code
     * cannot directly modify the internal list (encapsulation).
     */
    public List<EvidenceLog> getCustodyHistory() {
        return Collections.unmodifiableList(custodyHistory);
    }

    // ----- Getters and Setters -----

    public String getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(String evidenceId) {
        this.evidenceId = evidenceId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getCollectionDate() {
        return collectionDate;
    }

    public void setCollectionDate(LocalDate collectionDate) {
        this.collectionDate = collectionDate;
    }

    public String getCurrentCustodian() {
        return currentCustodian;
    }

    public void setCurrentCustodian(String currentCustodian) {
        this.currentCustodian = currentCustodian;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Evidence{")
          .append("evidenceId='").append(evidenceId).append('\'')
          .append(", description='").append(description).append('\'')
          .append(", collectionDate=").append(collectionDate)
          .append(", currentCustodian='").append(currentCustodian).append('\'')
          .append(", custodyHistory=[\n");
        for (EvidenceLog log : custodyHistory) {
            sb.append("    ").append(log).append("\n");
        }
        sb.append("]}");
        return sb.toString();
    }
}
