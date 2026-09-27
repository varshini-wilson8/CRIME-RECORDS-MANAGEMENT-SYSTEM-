import com.sun.net.httpserver.*;
import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * Explainable cross-case intelligence signals and defensible Investigation Readiness/Coverage Indicator.
 * Signals and readiness metrics are descriptive workflow aids and require investigator review.
 * No guilt, probability of truth, or risk conclusion is inferred by this endpoint.
 */
public final class CaseIntelligenceApiHandler implements HttpHandler {
    private final CaseDao cases;
    public CaseIntelligenceApiHandler(CaseDao cases){this.cases=cases;}

    public void handle(HttpExchange e) throws IOException {
        try {
            if(!"GET".equals(e.getRequestMethod())){HttpUtil.text(e,405,"{\"error\":\"Method not allowed\"}","application/json");return;}
            String caseId=param(e.getRequestURI().getRawQuery(),"caseId");
            if(caseId==null||caseId.isBlank()){HttpUtil.text(e,400,"{\"error\":\"caseId is required\"}","application/json");return;}
            if(cases.findAll().stream().noneMatch(c->c.getCaseId().equals(caseId))){HttpUtil.text(e,404,"{\"error\":\"Case not found\"}","application/json");return;}

            List<Signal> out=new ArrayList<>();
            Readiness readiness;

            try(Connection c=Database.connect()){
                // 1. Shared people across cases.
                String sql="SELECT p.id,TRIM(COALESCE(p.first_name,'')||' '||COALESCE(p.last_name,'')) name, " +
                           "COUNT(DISTINCT cp.case_id) case_count, group_concat(DISTINCT cp.case_id) case_ids " +
                           "FROM case_persons cp JOIN persons p ON p.id=cp.person_id " +
                           "WHERE p.id IN (SELECT person_id FROM case_persons WHERE case_id=?) " +
                           "GROUP BY p.id HAVING COUNT(DISTINCT cp.case_id)>1 ORDER BY case_count DESC";
                try(PreparedStatement p=c.prepareStatement(sql)){p.setString(1,caseId);try(ResultSet r=p.executeQuery()){while(r.next()){
                    out.add(new Signal("SHARED_PERSON","Person appears in multiple recorded cases",
                            "The recorded person \""+safe(r.getString("name"),r.getString("id"))+"\" is linked to "+r.getInt("case_count")+" cases.",
                            "PERSON",r.getString("id"),r.getString("case_ids"),"Review the person's role and context in each case before treating the connection as meaningful."));
                }}}
                // 2. Shared locations across cases.
                sql="SELECT l.id,l.name,COUNT(DISTINCT cl.case_id) case_count,group_concat(DISTINCT cl.case_id) case_ids " +
                    "FROM case_locations cl JOIN locations l ON l.id=cl.location_id WHERE l.id IN (SELECT location_id FROM case_locations WHERE case_id=?) " +
                    "GROUP BY l.id HAVING COUNT(DISTINCT cl.case_id)>1 ORDER BY case_count DESC";
                try(PreparedStatement p=c.prepareStatement(sql)){p.setString(1,caseId);try(ResultSet r=p.executeQuery()){while(r.next()){
                    out.add(new Signal("SHARED_LOCATION","Location appears in multiple recorded cases",
                            "The location \""+safe(r.getString("name"),r.getString("id"))+"\" is linked to "+r.getInt("case_count")+" cases.",
                            "LOCATION",r.getString("id"),r.getString("case_ids"),"Review dates, location roles, and independent case facts before treating this as a pattern."));
                }}}
                // 3. Shared vehicles across cases.
                sql="SELECT v.id,v.registration_number,COUNT(DISTINCT cv.case_id) case_count,group_concat(DISTINCT cv.case_id) case_ids " +
                    "FROM case_vehicles cv JOIN vehicles v ON v.id=cv.vehicle_id WHERE v.id IN (SELECT vehicle_id FROM case_vehicles WHERE case_id=?) " +
                    "GROUP BY v.id HAVING COUNT(DISTINCT cv.case_id)>1 ORDER BY case_count DESC";
                try(PreparedStatement p=c.prepareStatement(sql)){p.setString(1,caseId);try(ResultSet r=p.executeQuery()){while(r.next()){
                    out.add(new Signal("SHARED_VEHICLE","Vehicle appears in multiple recorded cases",
                            "Vehicle \""+safe(r.getString("registration_number"),r.getString("id"))+"\" is linked to "+r.getInt("case_count")+" cases.",
                            "VEHICLE",r.getString("id"),r.getString("case_ids"),"Review vehicle relationship types and timestamps before drawing investigative conclusions."));
                }}}
                // 4. Same crime type across recent cases.
                if(hasColumn(c,"cases","crime_type")){
                    sql="SELECT c.crime_type,COUNT(*) n,group_concat(c.id) case_ids FROM cases c WHERE c.id<>? AND c.crime_type IS NOT NULL AND c.crime_type<>'' AND c.crime_type=(SELECT crime_type FROM cases WHERE id=?) GROUP BY c.crime_type";
                    try(PreparedStatement p=c.prepareStatement(sql)){p.setString(1,caseId);p.setString(2,caseId);try(ResultSet r=p.executeQuery()){while(r.next()){
                        out.add(new Signal("SAME_CRIME_TYPE","Same recorded crime type exists in other cases",
                                "The selected case shares crime type \""+safe(r.getString(1),"unspecified")+"\" with "+r.getInt(2)+" other recorded case(s).",
                                "CASE",caseId,r.getString(3),"This is a broad classification signal, not evidence that cases are connected."));
                    }}}
                }
                // 5. Temporal proximity of case events.
                sql="SELECT a.id,a.title,a.event_time,b.case_id,b.id,b.title,b.event_time FROM case_events a JOIN case_events b ON b.case_id<>a.case_id " +
                    "WHERE a.case_id=? AND ABS((julianday(a.event_time)-julianday(b.event_time))*24.0)<=48 " +
                    "ORDER BY ABS(julianday(a.event_time)-julianday(b.event_time)) LIMIT 25";
                try(PreparedStatement p=c.prepareStatement(sql)){p.setString(1,caseId);try(ResultSet r=p.executeQuery()){while(r.next()){
                    out.add(new Signal("TEMPORAL_PROXIMITY","Timeline events occur within 48 hours across cases",
                            "Event \""+safe(r.getString(2),"event")+"\" in the selected case is within 48 hours of event \""+safe(r.getString(6),"event")+"\" in case "+r.getString(4)+".",
                            "EVENT",String.valueOf(r.getLong(1)),r.getString(4),"Review the underlying event sources and exact timestamps; temporal proximity alone does not establish a connection."));
                }}}

                // 6. Calculate Investigation Readiness / Coverage Indicator
                readiness = computeReadiness(c, caseId);
            }

            StringBuilder j=new StringBuilder("{\"caseId\":\"").append(q(caseId)).append("\",\"total\":").append(out.size())
                    .append(",\"signals\":[");
            for(int i=0;i<out.size();i++){if(i>0)j.append(',');j.append(out.get(i).json());}
            j.append("],\"readiness\":").append(readiness.json()).append("}");

            HttpUtil.text(e,200,j.toString(),"application/json");
        }catch(Exception ex){ex.printStackTrace();HttpUtil.text(e,500,"{\"error\":\"Unable to load case intelligence\"}","application/json");}
    }

    private static Readiness computeReadiness(Connection c, String caseId) throws SQLException {
        // 1. Case Setup (weight: 15%)
        int setupScore = 0;
        try (PreparedStatement p = c.prepareStatement("SELECT title, severity, status, assigned_officer_id FROM cases WHERE id=?")) {
            p.setString(1, caseId);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    if (r.getString("title") != null && !r.getString("title").isBlank()) setupScore += 25;
                    if (r.getString("severity") != null && !r.getString("severity").isBlank()) setupScore += 25;
                    if (r.getString("status") != null && !r.getString("status").isBlank()) setupScore += 25;
                    if (r.getString("assigned_officer_id") != null && !r.getString("assigned_officer_id").isBlank()) setupScore += 25;
                }
            }
        }

        // 2. People & Roles (weight: 15%)
        int peopleScore = 0;
        try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM case_persons WHERE case_id=?")) {
            p.setString(1, caseId);
            try (ResultSet r = p.executeQuery()) {
                int count = r.next() ? r.getInt(1) : 0;
                if (count >= 2) peopleScore = 100;
                else if (count == 1) peopleScore = 70;
            }
        }

        // 3. Timeline Coverage (weight: 20%)
        int timelineScore = 0;
        try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*), SUM(CASE WHEN verification_status IN ('VERIFIED','PENDING_REVIEW') THEN 1 ELSE 0 END) FROM case_events WHERE case_id=?")) {
            p.setString(1, caseId);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    int totalEvents = r.getInt(1);
                    int verifiedOrReviewed = r.getInt(2);
                    if (totalEvents >= 4) timelineScore += 60;
                    else if (totalEvents >= 2) timelineScore += 35;
                    else if (totalEvents == 1) timelineScore += 20;

                    if (totalEvents > 0) {
                        timelineScore += Math.round(((float) verifiedOrReviewed / totalEvents) * 40f);
                    }
                }
            }
        }
        timelineScore = Math.min(100, Math.max(0, timelineScore));

        // 4. Evidence & Custody (weight: 20%) - Custody-Aware & Integrity-Checked
        int evidenceScore = 0;
        String evQuery = "SELECT e.id, e.file_path, e.hash_verified_at, COUNT(l.id) AS log_count " +
                         "FROM evidence e LEFT JOIN evidence_logs l ON l.evidence_id = e.id " +
                         "WHERE e.case_id = ? GROUP BY e.id";
        try (PreparedStatement p = c.prepareStatement(evQuery)) {
            p.setString(1, caseId);
            try (ResultSet r = p.executeQuery()) {
                int evCount = 0;
                int totalLogs = 0;
                int completeEvidenceCount = 0;

                while (r.next()) {
                    evCount++;
                    String fPath = r.getString("file_path");
                    String verifiedAt = r.getString("hash_verified_at");
                    int logCount = r.getInt("log_count");
                    totalLogs += logCount;

                    boolean isDigital = fPath != null && !fPath.isBlank();
                    if (isDigital) {
                        // Digital evidence: file exists + hash verified
                        if (verifiedAt != null && !verifiedAt.isBlank()) {
                            completeEvidenceCount++;
                        }
                    } else {
                        // Physical evidence: no digital file + custody history exists
                        if (logCount > 0) {
                            completeEvidenceCount++;
                        }
                    }
                }

                if (evCount >= 1) evidenceScore += 50;
                if (totalLogs >= 2) evidenceScore += 30;
                else if (totalLogs == 1) evidenceScore += 15;

                // Credit full integrity/custody bonus only if ALL evidence items are complete
                if (evCount > 0 && completeEvidenceCount >= evCount) {
                    evidenceScore += 20;
                }
            }
        }
        evidenceScore = Math.min(100, Math.max(0, evidenceScore));

        // 5. Hypothesis Analysis (weight: 20%)
        int hypothesisScore = 0;
        try (PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(DISTINCT h.id), COUNT(DISTINCT he.id) " +
                "FROM investigation_hypotheses h LEFT JOIN hypothesis_evidence he ON he.hypothesis_id=h.id WHERE h.case_id=?")) {
            p.setString(1, caseId);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    int hCount = r.getInt(1);
                    int relCount = r.getInt(2);
                    if (hCount >= 3) hypothesisScore += 60;
                    else if (hCount >= 2) hypothesisScore += 50;
                    else if (hCount == 1) hypothesisScore += 25;

                    if (relCount >= 3) hypothesisScore += 40;
                    else if (relCount >= 1) hypothesisScore += 25;
                }
            }
        }
        hypothesisScore = Math.min(100, Math.max(0, hypothesisScore));

        // 6. Task Completion (weight: 10%)
        int taskScore = 0;
        try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*), SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END) FROM investigation_tasks WHERE case_id=?")) {
            p.setString(1, caseId);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    int total = r.getInt(1);
                    int completed = r.getInt(2);
                    if (total > 0) {
                        taskScore = Math.round(((float) completed / total) * 100f);
                    } else {
                        taskScore = 50;
                    }
                }
            }
        }
        taskScore = Math.min(100, Math.max(0, taskScore));

        int overall = Math.round(
                setupScore * 0.15f +
                peopleScore * 0.15f +
                timelineScore * 0.20f +
                evidenceScore * 0.20f +
                hypothesisScore * 0.20f +
                taskScore * 0.10f
        );

        return new Readiness(overall, setupScore, peopleScore, timelineScore, evidenceScore, hypothesisScore, taskScore);
    }

    private static final class Readiness {
        final int overall, setup, people, timeline, evidence, hypothesis, tasks;
        Readiness(int overall, int setup, int people, int timeline, int evidence, int hypothesis, int tasks) {
            this.overall = overall; this.setup = setup; this.people = people;
            this.timeline = timeline; this.evidence = evidence; this.hypothesis = hypothesis; this.tasks = tasks;
        }

        String json() {
            return "{\"overallScore\":" + overall +
                    ",\"label\":\"Investigation Coverage / Readiness Indicator\"" +
                    ",\"disclaimer\":\"This indicator describes recorded workflow coverage in CRMS. It does not represent probability of case resolution, truth, guilt, innocence, or investigative success.\"" +
                    ",\"breakdown\":{" +
                    "\"caseSetup\":{\"score\":" + setup + ",\"weight\":15,\"weighted\":" + (setup * 0.15f) + ",\"label\":\"Case Setup\"}," +
                    "\"peopleAndRoles\":{\"score\":" + people + ",\"weight\":15,\"weighted\":" + (people * 0.15f) + ",\"label\":\"People & Roles\"}," +
                    "\"timelineCoverage\":{\"score\":" + timeline + ",\"weight\":20,\"weighted\":" + (timeline * 0.20f) + ",\"label\":\"Timeline Coverage\"}," +
                    "\"evidenceAndCustody\":{\"score\":" + evidence + ",\"weight\":20,\"weighted\":" + (evidence * 0.20f) + ",\"label\":\"Evidence & Custody\"}," +
                    "\"hypothesisAnalysis\":{\"score\":" + hypothesis + ",\"weight\":20,\"weighted\":" + (hypothesis * 0.20f) + ",\"label\":\"Hypothesis Analysis\"}," +
                    "\"taskCompletion\":{\"score\":" + tasks + ",\"weight\":10,\"weighted\":" + (tasks * 0.10f) + ",\"label\":\"Task Completion\"}" +
                    "}}";
        }
    }

    private static String safe(String s,String fallback){return s==null||s.isBlank()?fallback:s;}
    private static String param(String raw,String name){if(raw==null)return null;for(String pair:raw.split("&")){String[]p=pair.split("=",2);if(p.length==2&&p[0].equals(name))return java.net.URLDecoder.decode(p[1],java.nio.charset.StandardCharsets.UTF_8);}return null;}
    private static String q(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
    private static boolean hasColumn(Connection c, String table, String col) {
        try (ResultSet r = c.createStatement().executeQuery("PRAGMA table_info(" + table + ")")) {
            while (r.next()) { if (col.equalsIgnoreCase(r.getString("name"))) return true; }
        } catch (Exception ignored) {}
        return false;
    }
    private static final class Signal {
        final String type, title, description, entityType, entityId, relatedCases, reviewNote;
        Signal(String type, String title, String description, String entityType, String entityId, String relatedCases, String reviewNote) {
            this.type = type; this.title = title; this.description = description;
            this.entityType = entityType; this.entityId = entityId; this.relatedCases = relatedCases; this.reviewNote = reviewNote;
        }
        String json() {
            return "{\"type\":\""+q(type)+"\",\"title\":\""+q(title)+"\",\"description\":\""+q(description)+"\",\"entityType\":\""+q(entityType)+"\",\"entityId\":\""+q(entityId)+"\",\"relatedCases\":\""+q(relatedCases)+"\",\"reviewNote\":\""+q(reviewNote)+"\"}";
        }
    }
}
