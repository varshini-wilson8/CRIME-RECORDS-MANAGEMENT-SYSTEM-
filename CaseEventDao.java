import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import model.CaseEvent;
import model.TimelineGap;

public final class CaseEventDao {
    public static final int GENERAL_GAP_THRESHOLD_MINUTES = 30;
    public static final int INCIDENT_REVIEW_GAP_THRESHOLD_MINUTES = 8;

    public List<CaseEvent> findByCaseId(String caseId) throws SQLException {
        List<CaseEvent> out = new ArrayList<>();
        String sql = "SELECT ce.id,ce.case_id,ce.event_type,ce.title,ce.description,ce.event_time," +
                "l.name location_name, TRIM(COALESCE(p.first_name,'') || ' ' || COALESCE(p.last_name,'')) person_name," +
                "ce.evidence_id,ce.source,ce.verification_status, COALESCE(ce.truth_level, 'REPORTED') truth_level " +
                "FROM case_events ce " +
                "LEFT JOIN locations l ON l.id=ce.location_id " +
                "LEFT JOIN persons p ON p.id=ce.person_id " +
                "WHERE ce.case_id=? ORDER BY ce.event_time ASC, ce.id ASC";
        try(Connection c=Database.connect(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1, caseId);
            try(ResultSet r=p.executeQuery()) {
                while(r.next()) {
                    String person = r.getString("person_name");
                    if(person != null && person.trim().isEmpty()) person = null;
                    out.add(new CaseEvent(r.getLong("id"), r.getString("case_id"), r.getString("event_type"),
                            r.getString("title"), r.getString("description"), r.getString("event_time"),
                            r.getString("location_name"), person, r.getString("evidence_id"),
                            r.getString("source"), r.getString("verification_status"), r.getString("truth_level")));
                }
            }
        }
        return out;
    }

    public List<TimelineGap> detectGaps(List<CaseEvent> events) {
        List<TimelineGap> gaps = new ArrayList<>();
        if (events == null || events.size() < 2) return gaps;

        // Identify Incident Review Window:
        // Locate any INCIDENT event, and set incident window to [incidentTime - 30m, incidentTime + 30m]
        LocalDateTime incidentTime = null;
        for (CaseEvent e : events) {
            if ("INCIDENT".equalsIgnoreCase(e.getEventType())) {
                try {
                    incidentTime = parseIso(e.getEventTime());
                    break;
                } catch (Exception ignored) {}
            }
        }
        LocalDateTime incidentStart = incidentTime != null ? incidentTime.minusMinutes(30) : null;
        LocalDateTime incidentEnd = incidentTime != null ? incidentTime.plusMinutes(30) : null;

        for (int i = 0; i < events.size() - 1; i++) {
            CaseEvent a = events.get(i);
            CaseEvent b = events.get(i + 1);
            try {
                LocalDateTime t1 = parseIso(a.getEventTime());
                LocalDateTime t2 = parseIso(b.getEventTime());
                if (t2.isBefore(t1)) continue;

                long diffMinutes = ChronoUnit.MINUTES.between(t1, t2);
                boolean inIncidentWindow = incidentStart != null && incidentEnd != null
                        && (!t1.isBefore(incidentStart) && !t1.isAfter(incidentEnd));

                int threshold = inIncidentWindow ? INCIDENT_REVIEW_GAP_THRESHOLD_MINUTES : GENERAL_GAP_THRESHOLD_MINUTES;
                if (diffMinutes > threshold) {
                    String time1Str = formatShort(t1);
                    String time2Str = formatShort(t2);
                    String windowType = inIncidentWindow ? "INCIDENT_REVIEW_WINDOW" : "GENERAL_WINDOW";
                    String label = "Recorded timeline gap (" + diffMinutes + " minutes)";
                    String disclaimer = "Absence of recorded coverage is a data observation and does not prove no activity occurred.";
                    String taskTitle = "Review available records for interval " + time1Str + "–" + time2Str;
                    String taskDesc = "Review available CCTV, access-control records, witness statements, or other records for the interval " + time1Str + "–" + time2Str + ".";
                    String priority = inIncidentWindow ? "HIGH" : "MEDIUM";

                    gaps.add(new TimelineGap(a.getEventTime(), b.getEventTime(), diffMinutes, windowType,
                            threshold, label, disclaimer, taskTitle, taskDesc, priority));
                }
            } catch (Exception ignored) {}
        }
        return gaps;
    }

    public long save(String caseId, String eventType, String title, String description, String eventTime,
                     String source, String verificationStatus) throws SQLException {
        return save(caseId, eventType, title, description, eventTime, source, verificationStatus, "REPORTED");
    }

    public long save(String caseId, String eventType, String title, String description, String eventTime,
                     String source, String verificationStatus, String truthLevel) throws SQLException {
        String sql="INSERT INTO case_events(case_id,event_type,title,description,event_time,source,verification_status,truth_level) VALUES(?,?,?,?,?,?,?,?)";
        try(Connection c=Database.connect(); PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1,caseId); p.setString(2,eventType); p.setString(3,title); p.setString(4,description);
            p.setString(5,eventTime); p.setString(6,source); p.setString(7,verificationStatus);
            p.setString(8, truthLevel != null ? truthLevel : "REPORTED");
            p.executeUpdate();
            try(ResultSet r=p.getGeneratedKeys()){if(r.next()) return r.getLong(1);}
        }
        return -1;
    }

    private static LocalDateTime parseIso(String iso) {
        if (iso == null || iso.isBlank()) throw new IllegalArgumentException("Empty timestamp");
        String trimmed = iso.trim().replace(" ", "T");
        if (trimmed.length() == 10) trimmed += "T00:00:00";
        if (trimmed.length() == 16) trimmed += ":00";
        return LocalDateTime.parse(trimmed);
    }

    private static String formatShort(LocalDateTime dt) {
        return String.format("%02d:%02d", dt.getHour(), dt.getMinute());
    }
}
