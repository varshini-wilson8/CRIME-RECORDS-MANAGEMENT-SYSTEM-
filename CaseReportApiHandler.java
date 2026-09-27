import com.sun.net.httpserver.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import model.*;

public class CaseReportApiHandler implements HttpHandler {
    private final CaseReportDao reports = new CaseReportDao();
    private final CaseDao cases;
    private final SessionManager sessions;
    private final AuditLogDao audit = new AuditLogDao();

    public CaseReportApiHandler(CaseDao c, SessionManager s) {
        this.cases = c;
        this.sessions = s;
    }

    public void handle(HttpExchange e) throws IOException {
        try {
            String query = e.getRequestURI().getRawQuery();
            String caseId = param(query, "caseId");
            if (caseId == null || caseId.isBlank()) {
                HttpUtil.text(e, 400, "{\"error\":\"caseId is required\"}", "application/json");
                return;
            }
            if (cases.findAll().stream().noneMatch(x -> x.getCaseId().equals(caseId))) {
                HttpUtil.text(e, 404, "{\"error\":\"Case not found\"}", "application/json");
                return;
            }

            String m = e.getRequestMethod();
            if ("GET".equalsIgnoreCase(m)) {
                CaseReport r = reports.latest(caseId);
                HttpUtil.text(e, 200, "{\"report\":" + (r == null ? "null" : json(r)) + ",\"validation\":" + validation(caseId) + "}", "application/json");
                return;
            }

            Map<String, String> f = HttpUtil.form(e);
            UserSession u = session(e);
            String by = u != null ? u.getUserId() : "SYSTEM";

            if ("POST".equalsIgnoreCase(m)) {
                String type = valid(f.get("reportType"), new String[]{"INCIDENT_REPORT", "INVESTIGATION_REPORT", "FORENSIC_REPORT", "SUPERVISOR_REPORT", "FINAL_REPORT", "COURT_PACKAGE"}, "INVESTIGATION_REPORT");
                String title = f.getOrDefault("title", "Investigation Case Package - " + caseId);

                Dossier dossier = buildDossier(caseId, title, by);
                long id = reports.create(caseId, type, title, dossier.fullContent, by);

                audit.record(by, "PACKAGE_GENERATED", "REPORT", String.valueOf(id),
                        "CASE_ID=" + caseId + "; type=" + type + "; canonicalHash=" + dossier.canonicalHash + "; packageHash=" + dossier.packageHash);

                HttpUtil.text(e, 201, "{\"id\":" + id + ",\"canonicalHash\":\"" + dossier.canonicalHash + "\",\"packageHash\":\"" + dossier.packageHash + "\"}", "application/json");
                return;
            }

            if ("PATCH".equalsIgnoreCase(m)) {
                long id = Long.parseLong(f.getOrDefault("id", "0"));
                String st = valid(f.get("status"), new String[]{"DRAFT", "UNDER_REVIEW", "APPROVED", "ARCHIVED"}, null);
                if (id <= 0 || st == null) {
                    HttpUtil.text(e, 400, "{\"error\":\"valid id and status are required\"}", "application/json");
                    return;
                }
                reports.updateStatus(id, st, by);
                audit.record(by, "REPORT_STATUS_CHANGED", "REPORT", String.valueOf(id), "CASE_ID=" + caseId + "; status=" + st);
                HttpUtil.text(e, 200, "{\"id\":" + id + ",\"status\":\"" + q(st) + "\"}", "application/json");
                return;
            }

            HttpUtil.text(e, 405, "{\"error\":\"Method not allowed\"}", "application/json");
        } catch (Exception ex) {
            ex.printStackTrace();
            HttpUtil.text(e, 500, "{\"error\":\"Unable to process case report\"}", "application/json");
        }
    }

    private static final class Dossier {
        final String canonicalHash;
        final String packageHash;
        final String fullContent;
        Dossier(String canonicalHash, String packageHash, String fullContent) {
            this.canonicalHash = canonicalHash;
            this.packageHash = packageHash;
            this.fullContent = fullContent;
        }
    }

    private Dossier buildDossier(String id, String packageTitle, String generatedBy) throws Exception {
        // Step 1: Build the canonical deterministic record body (excluding generation timestamp/user)
        StringBuilder canon = new StringBuilder();
        canon.append("--- BEGIN CANONICAL CASE RECORD SET ---\n");

        try (Connection c = Database.connect()) {
            // 1. Overview
            try (PreparedStatement p = c.prepareStatement("SELECT c.*, o.name officer FROM cases c LEFT JOIN officers o ON o.id=c.assigned_officer_id WHERE c.id=?")) {
                p.setString(1, id);
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) {
                        canon.append("[CASE_OVERVIEW]\n")
                             .append("ID: ").append(r.getString("id")).append("\n")
                             .append("TITLE: ").append(r.getString("title")).append("\n")
                             .append("SEVERITY: ").append(r.getString("severity")).append("\n")
                             .append("STATUS: ").append(r.getString("status")).append("\n")
                             .append("OPENING_DATE: ").append(r.getString("opening_date")).append("\n")
                             .append("OFFICER: ").append(n(r.getString("officer"))).append("\n")
                             .append("DESCRIPTION: ").append(n(r.getString("description"))).append("\n\n");
                    }
                }
            }

            // 2. People (stable order: last_name ASC, first_name ASC)
            canon.append("[PEOPLE_INTELLIGENCE]\n");
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT p.id, p.first_name, p.last_name, cp.role, COALESCE(cp.notes,'') notes, COALESCE(p.phone,'') phone, COALESCE(p.physical_description,'') pdesc " +
                    "FROM case_persons cp JOIN persons p ON p.id=cp.person_id WHERE cp.case_id=? ORDER BY p.last_name ASC, p.first_name ASC, p.id ASC")) {
                p.setString(1, id);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        canon.append("- ").append(n(r.getString("last_name"))).append(", ").append(n(r.getString("first_name")))
                             .append(" [").append(r.getString("role")).append("] ID:").append(r.getString("id"))
                             .append(" | Notes: ").append(r.getString("notes"))
                             .append(" | Contact: ").append(r.getString("phone"))
                             .append(" | Description: ").append(r.getString("pdesc")).append("\n");
                    }
                }
            }
            canon.append("\n");

            // 3. Epistemological Timeline (stable order: event_time ASC, id ASC)
            canon.append("[EPISTEMOLOGICAL_TIMELINE]\n");
            List<CaseEvent> evList = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT ce.id, ce.case_id, ce.event_type, ce.title, ce.description, ce.event_time, " +
                    "l.name loc_name, TRIM(COALESCE(p.first_name,'')||' '||COALESCE(p.last_name,'')) person_name, " +
                    "ce.evidence_id, ce.source, ce.verification_status, COALESCE(ce.truth_level,'REPORTED') truth_level " +
                    "FROM case_events ce LEFT JOIN locations l ON l.id=ce.location_id LEFT JOIN persons p ON p.id=ce.person_id " +
                    "WHERE ce.case_id=? ORDER BY ce.event_time ASC, ce.id ASC")) {
                p.setString(1, id);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        CaseEvent ce = new CaseEvent(r.getLong("id"), r.getString("case_id"), r.getString("event_type"),
                                r.getString("title"), r.getString("description"), r.getString("event_time"),
                                r.getString("loc_name"), r.getString("person_name"), r.getString("evidence_id"),
                                r.getString("source"), r.getString("verification_status"), r.getString("truth_level"));
                        evList.add(ce);
                        canon.append("- ").append(ce.getEventTime())
                             .append(" | [").append(ce.getTruthLevel()).append(" / ").append(ce.getVerificationStatus()).append("]")
                             .append(" | ").append(ce.getEventType()).append(": ").append(ce.getTitle())
                             .append(" | Source: ").append(n(ce.getSource()))
                             .append(" | Details: ").append(n(ce.getDescription())).append("\n");
                    }
                }
            }
            canon.append("\n");

            // 4. Timeline Gaps
            CaseEventDao eventDao = new CaseEventDao();
            List<TimelineGap> gaps = eventDao.detectGaps(evList);
            canon.append("[RECORDED_TIMELINE_GAPS]\n");
            if (gaps.isEmpty()) {
                canon.append("No recorded timeline gaps exceeding configured review thresholds.\n");
            } else {
                for (TimelineGap g : gaps) {
                    canon.append("- ").append(g.getFromTime()).append(" -> ").append(g.getToTime())
                         .append(" (").append(g.getDurationMinutes()).append(" min) [").append(g.getWindowType()).append("]")
                         .append(" | Suggested action: ").append(g.getSuggestedTaskTitle()).append("\n");
                }
            }
            canon.append("\n");

            // 5. Evidence & Integrity Manifest (stable order: id ASC)
            canon.append("[EVIDENCE_AND_CUSTODY_MANIFEST]\n");
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT id, description, collection_date, current_custodian, file_path, file_hash_sha256, hash_verified_at, file_size_bytes " +
                    "FROM evidence WHERE case_id=? ORDER BY id ASC")) {
                p.setString(1, id);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        String eid = r.getString("id");
                        String fPath = r.getString("file_path");
                        String fName = fPath != null && !fPath.isBlank() ? java.nio.file.Path.of(fPath).getFileName().toString() : "NONE";
                        String hash = r.getString("file_hash_sha256");
                        String vAt = r.getString("hash_verified_at");
                        canon.append("- EVIDENCE: ").append(eid).append(" | Description: ").append(r.getString("description"))
                             .append(" | Custodian: ").append(r.getString("current_custodian"))
                             .append(" | File: ").append(fName)
                             .append(" | SHA-256: ").append(hash != null ? hash : "NOT_ATTACHED")
                             .append(" | Verified: ").append(vAt != null ? vAt : "NOT_VERIFIED").append("\n");

                        // Append custody history in stable chronological order
                        try (PreparedStatement lp = c.prepareStatement("SELECT timestamp, action_type, from_custodian, to_custodian, remarks FROM evidence_logs WHERE evidence_id=? ORDER BY timestamp ASC, id ASC")) {
                            lp.setString(1, eid);
                            try (ResultSet lr = lp.executeQuery()) {
                                while (lr.next()) {
                                    canon.append("    * Custody: ").append(lr.getString("timestamp"))
                                         .append(" | ").append(lr.getString("action_type"))
                                         .append(" | From: ").append(lr.getString("from_custodian"))
                                         .append(" -> To: ").append(lr.getString("to_custodian"))
                                         .append(" | Remarks: ").append(lr.getString("remarks")).append("\n");
                                }
                            }
                        }
                    }
                }
            }
            canon.append("\n");

            // 6. ACH Hypotheses & Matrix (stable order: hypothesis_code ASC)
            canon.append("[ACH_ANALYSIS_OF_COMPETING_HYPOTHESES]\n");
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT id, hypothesis_code, title, status, description FROM investigation_hypotheses WHERE case_id=? ORDER BY hypothesis_code ASC")) {
                p.setString(1, id);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        long hid = r.getLong("id");
                        canon.append("- HYPOTHESIS ").append(r.getString("hypothesis_code")).append(": ").append(r.getString("title"))
                             .append(" [Status: ").append(r.getString("status")).append("]\n")
                             .append("    Description: ").append(n(r.getString("description"))).append("\n");

                        try (PreparedStatement heP = c.prepareStatement(
                                "SELECT evidence_id, relationship_type, notes FROM hypothesis_evidence WHERE hypothesis_id=? ORDER BY evidence_id ASC")) {
                            heP.setLong(1, hid);
                            try (ResultSet heR = heP.executeQuery()) {
                                while (heR.next()) {
                                    canon.append("    * Evidence ").append(heR.getString("evidence_id"))
                                         .append(" -> [").append(heR.getString("relationship_type")).append("] ")
                                         .append(n(heR.getString("notes"))).append("\n");
                                }
                            }
                        }
                    }
                }
            }
            canon.append("\n");

            // 7. Tasks (stable order: id ASC)
            canon.append("[INVESTIGATION_TASKS]\n");
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT title, priority, status, due_date FROM investigation_tasks WHERE case_id=? ORDER BY id ASC")) {
                p.setString(1, id);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        canon.append("- ").append(r.getString("title"))
                             .append(" | Priority: ").append(r.getString("priority"))
                             .append(" | Status: ").append(r.getString("status"))
                             .append(" | Due: ").append(n(r.getString("due_date"))).append("\n");
                    }
                }
            }
            canon.append("\n--- END CANONICAL CASE RECORD SET ---\n");
        }

        // Calculate deterministic Canonical Record Set SHA-256
        String canonicalHash = EvidenceDao.computeSha256(canon.toString().getBytes(StandardCharsets.UTF_8));

        // Step 2: Build the full export package file content (with header, generator, timestamp, disclaimers)
        String timestamp = java.time.Instant.now().toString();
        StringBuilder pkg = new StringBuilder();
        pkg.append("================================================================================\n")
           .append("CRMS 2.0 — INVESTIGATION CASE PACKAGE / COURT PREPARATION PACKAGE\n")
           .append("================================================================================\n\n")
           .append("PACKAGE TITLE:               ").append(packageTitle).append("\n")
           .append("CASE IDENTIFIER:             ").append(id).append("\n")
           .append("EXPORT GENERATED AT:         ").append(timestamp).append("\n")
           .append("GENERATING OFFICER / USER:   ").append(generatedBy != null ? generatedBy : "SYSTEM").append("\n")
           .append("CANONICAL RECORD SHA-256:    ").append(canonicalHash).append("\n\n")
           .append("MANDATORY LEGAL DISCLAIMER:\n")
           .append("This package is an export of records maintained by CRMS 2.0. Its integrity\n")
           .append("hash verifies the generated package content at the stated time. It does not\n")
           .append("constitute judicial certification, legal authentication, or an independent\n")
           .append("determination of truth.\n")
           .append("--------------------------------------------------------------------------------\n\n")
           .append(canon.toString());

        // Calculate Exported Package File SHA-256
        String packageHash = EvidenceDao.computeSha256(pkg.toString().getBytes(StandardCharsets.UTF_8));
        pkg.append("\nEXPORTED PACKAGE FILE SHA-256: ").append(packageHash).append("\n")
           .append("================================================================================\n");

        return new Dossier(canonicalHash, packageHash, pkg.toString());
    }

    private String validation(String id) throws Exception {
        int people = 0, events = 0, evidence = 0, openFindings = 0, openTasks = 0, hypotheses = 0;
        try (Connection c = Database.connect()) {
            people = count(c, "SELECT COUNT(*) FROM case_persons WHERE case_id=?", id);
            events = count(c, "SELECT COUNT(*) FROM case_events WHERE case_id=?", id);
            evidence = count(c, "SELECT COUNT(*) FROM evidence WHERE case_id=?", id);
            openFindings = count(c, "SELECT COUNT(*) FROM investigation_findings WHERE case_id=? AND status NOT IN ('RESOLVED')", id);
            openTasks = count(c, "SELECT COUNT(*) FROM investigation_tasks WHERE case_id=? AND status NOT IN ('COMPLETED','CANCELLED')", id);
            hypotheses = count(c, "SELECT COUNT(*) FROM investigation_hypotheses WHERE case_id=?", id);
        }
        return "{\"people\":" + people + ",\"events\":" + events + ",\"evidence\":" + evidence +
                ",\"openFindings\":" + openFindings + ",\"openTasks\":" + openTasks + ",\"hypotheses\":" + hypotheses +
                ",\"ready\":" + (people > 0 && events > 0 && evidence > 0) + "}";
    }

    private int count(Connection c, String q, String id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(q)) {
            p.setString(1, id);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            }
        }
    }

    private UserSession session(HttpExchange e) {
        String t = HttpUtil.cookie(e, "CCECS_SESSION");
        return t == null ? null : sessions.get(t).orElse(null);
    }

    private static String n(String s) { return s == null ? "Not recorded" : s; }
    private static String q(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"); }
    private static String valid(String v, String[] a, String d) {
        if (v == null || v.isBlank()) return d;
        for (String x : a) if (x.equalsIgnoreCase(v)) return x;
        return d;
    }
    private static String param(String raw, String name) {
        if (raw == null) return null;
        for (String pair : raw.split("&")) {
            String[] p = pair.split("=", 2);
            if (p.length == 2 && p[0].equals(name)) return java.net.URLDecoder.decode(p[1], java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String json(CaseReport r) {
        return "{\"id\":" + r.getId() + ",\"caseId\":\"" + q(r.getCaseId()) + "\",\"reportType\":\"" + q(r.getReportType()) +
                "\",\"title\":\"" + q(r.getTitle()) + "\",\"content\":\"" + q(r.getContent()) + "\",\"status\":\"" + q(r.getStatus()) +
                "\",\"createdBy\":\"" + q(r.getCreatedBy()) + "\",\"approvedBy\":\"" + q(r.getApprovedBy()) +
                "\",\"createdAt\":\"" + q(r.getCreatedAt()) + "\",\"updatedAt\":\"" + q(r.getUpdatedAt()) + "\"}";
    }
}
