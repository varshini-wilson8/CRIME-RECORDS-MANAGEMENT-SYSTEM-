import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public final class SecureDataHandler implements HttpHandler {
    private final String kind;
    private final AuditLogDao audit = new AuditLogDao();
    private final SessionManager sessions;
    private final EvidenceDao evidenceDao = new EvidenceDao();

    public SecureDataHandler(String kind) { this(kind, null); }
    public SecureDataHandler(String kind, SessionManager sessions) { this.kind = kind; this.sessions = sessions; }

    public void handle(HttpExchange e) throws IOException {
        try {
            if (kind.equals("officers")) { officers(e); return; }
            if (kind.equals("suspects")) { suspects(e); return; }
            evidence(e);
        } catch (Exception x) {
            x.printStackTrace();
            HttpUtil.text(e, 400, "{\"error\":\"Request could not be completed\"}", "application/json");
        }
    }

    private void officers(HttpExchange e) throws Exception {
        StringBuilder j = new StringBuilder("[");
        for (model.Officer o : new OfficerDao().findAll()) {
            if (j.length() > 1) j.append(',');
            j.append("{\"id\":\"").append(q(o.getId()))
             .append("\",\"name\":\"").append(q(o.getName()))
             .append("\",\"contact\":\"").append(q(o.getContact()))
             .append("\",\"badge\":\"").append(q(o.getBadgeNumber()))
             .append("\",\"activeCases\":").append(o.getActiveCaseCount()).append('}');
        }
        HttpUtil.text(e, 200, j.append(']').toString(), "application/json");
    }

    private void suspects(HttpExchange e) throws Exception {
        StringBuilder j = new StringBuilder("[");
        for (String[] s : new SuspectDao().rows()) {
            if (j.length() > 1) j.append(',');
            j.append("{\"id\":\"").append(q(s[0]))
             .append("\",\"name\":\"").append(q(s[1]))
             .append("\",\"risk\":\"").append(q(s[2]))
             .append("\",\"repeatOffender\":").append(s[3].equals("Yes")).append('}');
        }
        HttpUtil.text(e, 200, j.append(']').toString(), "application/json");
    }

    private void evidence(HttpExchange e) throws Exception {
        if (e.getRequestMethod().equals("GET")) {
            HttpUtil.text(e, 200, evidenceJson(), "application/json");
            return;
        }

        Map<String, String> f = HttpUtil.form(e);
        String action = f.getOrDefault("action", "");
        String uid = sessionUser(e);

        // 1. Verify Hash Action
        if ("reverify".equalsIgnoreCase(action) || "verify_hash".equalsIgnoreCase(action)) {
            String eid = f.get("evidenceId");
            if (eid == null || eid.isBlank()) {
                HttpUtil.text(e, 400, "{\"error\":\"evidenceId is required\"}", "application/json");
                return;
            }
            EvidenceDao.HashResult res = evidenceDao.verifyEvidenceHash(eid);
            if ("VERIFIED_MATCH".equals(res.status)) {
                audit.record(uid, "EVIDENCE_HASH_VERIFIED", "EVIDENCE", eid,
                        "SHA-256 match verified: " + res.storedHash + "; file=" + res.fileName);
            } else if ("INTEGRITY_MISMATCH".equals(res.status)) {
                audit.record(uid, "EVIDENCE_INTEGRITY_MISMATCH", "EVIDENCE", eid,
                        "CRITICAL INTEGRITY MISMATCH: recorded=" + res.storedHash + " vs computed=" + res.computedHash + "; file=" + res.fileName);
            }
            StringBuilder b = new StringBuilder("{")
                    .append("\"status\":\"").append(res.status).append("\"")
                    .append(",\"storedHash\":").append(res.storedHash != null ? "\"" + q(res.storedHash) + "\"" : "null")
                    .append(",\"computedHash\":").append(res.computedHash != null ? "\"" + q(res.computedHash) + "\"" : "null")
                    .append(",\"fileName\":").append(res.fileName != null ? "\"" + q(res.fileName) + "\"" : "null")
                    .append(",\"fileSizeBytes\":").append(res.fileSizeBytes != null ? res.fileSizeBytes : "null")
                    .append(",\"message\":\"").append(q(res.message)).append("\"")
                    .append(",\"verifiedAt\":").append(res.verifiedAt != null ? "\"" + q(res.verifiedAt) + "\"" : "null")
                    .append("}");
            HttpUtil.text(e, 200, b.toString(), "application/json");
            return;
        }

        // 2. Transfer Custody
        if (f.containsKey("transferTo")) {
            String from;
            try (Connection c = Database.connect()) {
                try (PreparedStatement p = c.prepareStatement("SELECT current_custodian FROM evidence WHERE id=?")) {
                    p.setString(1, f.get("evidenceId"));
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Evidence not found");
                        from = r.getString(1);
                    }
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE evidence SET current_custodian=? WHERE id=?")) {
                    p.setString(1, f.get("transferTo"));
                    p.setString(2, f.get("evidenceId"));
                    p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO evidence_logs(evidence_id,timestamp,action_type,from_custodian,to_custodian,remarks) VALUES(?,?,?,?,?,?)")) {
                    p.setString(1, f.get("evidenceId"));
                    p.setString(2, LocalDateTime.now().toString());
                    p.setString(3, "TRANSFER");
                    p.setString(4, from);
                    p.setString(5, f.get("transferTo"));
                    p.setString(6, f.getOrDefault("remarks", ""));
                    p.executeUpdate();
                }
            }
            audit.record(uid, "EVIDENCE_CUSTODY_TRANSFERRED", "EVIDENCE", f.get("evidenceId"),
                    "CASE_ID=" + f.get("caseId") + "; from=" + from + "; to=" + f.get("transferTo") + "; remarks=" + f.get("remarks"));
            HttpUtil.text(e, 200, "{\"message\":\"Custody transferred\"}", "application/json");
            return;
        }

        // 3. Attach Demo Fixture Action
        if ("attach_fixture".equalsIgnoreCase(action)) {
            String eid = f.get("evidenceId");
            if (eid == null || eid.isBlank()) {
                HttpUtil.text(e, 400, "{\"error\":\"evidenceId is required\"}", "application/json");
                return;
            }
            Path dir = Path.of("data", "evidence");
            Files.createDirectories(dir);
            Path fixture = dir.resolve(eid + "_demo_cctv_frame.dat");
            String content = "[DEMO/TEST DATA - FICTIONAL EVIDENCE FIXTURE FOR CRMS INTEGRITY TESTING]\n" +
                    "Evidence-ID: " + eid + "\n" +
                    "Case-ID: " + f.getOrDefault("caseId", "C-2026-041") + "\n" +
                    "Recorded-Camera: Gallery Entrance Cam 01\n" +
                    "Capture-Window: 2026-09-14T18:30:00 to 18:45:00\n" +
                    "Status: Fictional demonstration fixture for technical integrity verification\n";
            Files.writeString(fixture, content);
            String hash = EvidenceDao.computeFileSha256(fixture);
            long sz = Files.size(fixture);
            String now = java.time.Instant.now().toString();

            try (Connection c = Database.connect();
                 PreparedStatement p = c.prepareStatement("UPDATE evidence SET file_path=?, file_hash_sha256=?, hash_algorithm='SHA-256', hash_verified_at=?, file_size_bytes=? WHERE id=?")) {
                p.setString(1, fixture.toString());
                p.setString(2, hash);
                p.setString(3, now);
                p.setLong(4, sz);
                p.setString(5, eid);
                p.executeUpdate();
            }

            audit.record(uid, "EVIDENCE_FIXTURE_ATTACHED", "EVIDENCE", eid,
                    "Attached demo fixture " + fixture.getFileName() + " with SHA-256 " + hash);

            HttpUtil.text(e, 200, "{\"status\":\"VERIFIED_MATCH\",\"fileName\":\"" + fixture.getFileName() + "\",\"fileHash\":\"" + hash + "\"}", "application/json");
            return;
        }

        // 4. Create New Evidence
        try (Connection c = Database.connect();
             PreparedStatement p = c.prepareStatement("INSERT INTO evidence(id,case_id,description,collection_date,current_custodian) VALUES(?,?,?,?,?)")) {
            p.setString(1, f.get("evidenceId"));
            p.setString(2, f.get("caseId"));
            p.setString(3, f.get("description"));
            p.setString(4, LocalDate.now().toString());
            p.setString(5, f.get("custodian"));
            p.executeUpdate();
        }
        audit.record(uid, "EVIDENCE_ADDED", "EVIDENCE", f.get("evidenceId"),
                "CASE_ID=" + f.get("caseId") + "; custodian=" + f.get("custodian") + "; description=" + f.get("description"));
        HttpUtil.text(e, 201, "{\"message\":\"Evidence added\"}", "application/json");
    }

    private String evidenceJson() throws SQLException {
        StringBuilder j = new StringBuilder("[");
        String sql = "SELECT e.id, e.case_id, e.description, e.current_custodian, " +
                "e.file_path, e.file_hash_sha256, e.hash_algorithm, e.hash_verified_at, e.file_size_bytes, " +
                "l.timestamp, l.from_custodian, l.to_custodian, l.remarks " +
                "FROM evidence e " +
                "LEFT JOIN evidence_logs l ON l.evidence_id = e.id " +
                "ORDER BY e.id, l.id";

        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            String id = null;
            StringBuilder cur = null;
            while (r.next()) {
                String curId = r.getString(1);
                if (!curId.equals(id)) {
                    if (cur != null) {
                        if (j.length() > 1) j.append(',');
                        j.append(cur).append("]}");
                    }
                    id = curId;
                    String fPath = r.getString(5);
                    String fName = (fPath != null && !fPath.isBlank()) ? Path.of(fPath).getFileName().toString() : null;
                    Number fSizeNum = (Number) r.getObject(9);
                    Long fSize = fSizeNum != null ? fSizeNum.longValue() : null;
                    String hash = r.getString(6);
                    String algo = r.getString(7) != null ? r.getString(7) : "SHA-256";
                    String verifiedAt = r.getString(8);

                    String hashStatus;
                    if (fPath == null || fPath.isBlank()) {
                        hashStatus = "NO_FILE_ATTACHED";
                    } else if (verifiedAt != null && !verifiedAt.isBlank()) {
                        hashStatus = "VERIFIED_MATCH";
                    } else if (hash != null && !hash.isBlank()) {
                        hashStatus = "PENDING_VERIFICATION";
                    } else {
                        hashStatus = "HASH_UNAVAILABLE";
                    }

                    cur = new StringBuilder("{\"evidenceId\":\"").append(q(id))
                            .append("\",\"caseId\":\"").append(q(r.getString(2)))
                            .append("\",\"description\":\"").append(q(r.getString(3)))
                            .append("\",\"custodian\":\"").append(q(r.getString(4)))
                            .append("\",\"fileName\":").append(fName != null ? "\"" + q(fName) + "\"" : "null")
                            .append(",\"fileSizeBytes\":").append(fSize != null ? fSize : "null")
                            .append(",\"fileHash\":").append(hash != null ? "\"" + q(hash) + "\"" : "null")
                            .append(",\"hashAlgorithm\":\"").append(q(algo))
                            .append("\",\"hashVerifiedAt\":").append(verifiedAt != null ? "\"" + q(verifiedAt) + "\"" : "null")
                            .append(",\"hashStatus\":\"").append(hashStatus)
                            .append("\",\"logs\":[");
                }
                if (r.getString(10) != null) {
                    if (cur.charAt(cur.length() - 1) != '[') cur.append(',');
                    cur.append("{\"time\":\"").append(q(r.getString(10)))
                       .append("\",\"from\":\"").append(q(r.getString(11)))
                       .append("\",\"to\":\"").append(q(r.getString(12)))
                       .append("\",\"remarks\":\"").append(q(r.getString(13)))
                       .append("\"}");
                }
            }
            if (cur != null) {
                if (j.length() > 1) j.append(',');
                j.append(cur).append("]}");
            }
        }
        return j.append(']').toString();
    }

    private String sessionUser(HttpExchange e) {
        String t = HttpUtil.cookie(e, "CCECS_SESSION");
        return t == null ? "SYSTEM" : sessions.get(t).map(UserSession::getUserId).orElse("SYSTEM");
    }

    private static String q(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
