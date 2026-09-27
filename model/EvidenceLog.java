package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single custody-transfer event for a piece of Evidence.
 * A list of these forms the full chain-of-custody trail.
 */
public class EvidenceLog {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LocalDateTime timestamp;
    private String transferredFrom;
    private String transferredTo;
    private String remarks;

    public EvidenceLog(String transferredFrom, String transferredTo, String remarks) {
        this.timestamp = LocalDateTime.now();
        this.transferredFrom = transferredFrom;
        this.transferredTo = transferredTo;
        this.remarks = remarks;
    }

    public EvidenceLog(LocalDateTime timestamp, String transferredFrom,
                        String transferredTo, String remarks) {
        this.timestamp = timestamp;
        this.transferredFrom = transferredFrom;
        this.transferredTo = transferredTo;
        this.remarks = remarks;
    }

    // ----- Getters and Setters -----

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getTransferredFrom() {
        return transferredFrom;
    }

    public void setTransferredFrom(String transferredFrom) {
        this.transferredFrom = transferredFrom;
    }

    public String getTransferredTo() {
        return transferredTo;
    }

    public void setTransferredTo(String transferredTo) {
        this.transferredTo = transferredTo;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    @Override
    public String toString() {
        return "[" + timestamp.format(FORMATTER) + "] " +
                transferredFrom + " -> " + transferredTo +
                " (" + remarks + ")";
    }
}
