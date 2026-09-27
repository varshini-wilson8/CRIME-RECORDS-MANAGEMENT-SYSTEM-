import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import model.Hypothesis;
import model.HypothesisEvidence;

public final class HypothesisApiHandler implements HttpHandler {
    private final HypothesisDao dao;
    private final CaseDao cases;
    private final SessionManager sessions;
    private final AuditLogDao audit = new AuditLogDao();

    public HypothesisApiHandler(HypothesisDao dao, CaseDao cases, SessionManager sessions) {
        this.dao = dao;
        this.cases = cases;
        this.sessions = sessions;
    }

    public void handle(HttpExchange e) throws IOException {
        try {
            String query = e.getRequestURI().getRawQuery();
            String caseId = param(query, "caseId");
            if (caseId == null || caseId.isBlank()) {
                HttpUtil.text(e, 400, "{\"error\":\"caseId is required\"}", "application/json");
                return;
            }
            boolean exists = cases.findAll().stream().anyMatch(c -> c.getCaseId().equals(caseId));
            if (!exists) {
                HttpUtil.text(e, 404, "{\"error\":\"Case not found\"}", "application/json");
                return;
            }

            UserSession u = session(e);
            String actor = u != null ? u.getUserId() : "SYSTEM";

            String method = e.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(e, caseId);
                return;
            }

            Map<String, String> f = HttpUtil.form(e);
            if ("POST".equalsIgnoreCase(method)) {
                String action = f.getOrDefault("action", "hypothesis");
                if ("relationship".equalsIgnoreCase(action)) {
                    long hypothesisId = Long.parseLong(f.getOrDefault("hypothesisId", "0"));
                    String evidenceId = f.get("evidenceId");
                    String relType = f.get("relationshipType");
                    if (hypothesisId <= 0 || evidenceId == null || evidenceId.isBlank() || relType == null || relType.isBlank()) {
                        HttpUtil.text(e, 400, "{\"error\":\"hypothesisId, evidenceId, and relationshipType are required\"}", "application/json");
                        return;
                    }
                    String validRel = valid(relType.toUpperCase(), new String[]{"SUPPORTS", "CONTRADICTS", "CONTEXT", "UNASSESSED"}, null);
                    if (validRel == null) {
                        HttpUtil.text(e, 400, "{\"error\":\"Invalid relationshipType\"}", "application/json");
                        return;
                    }

                    // Enforce Cross-Case Mutation Isolation
                    if (!hypothesisBelongsToCase(hypothesisId, caseId)) {
                        HttpUtil.text(e, 400, "{\"error\":\"Cross-case isolation violation: hypothesis " + hypothesisId + " does not belong to case " + caseId + "\"}", "application/json");
                        return;
                    }
                    if (!evidenceBelongsToCase(evidenceId, caseId)) {
                        HttpUtil.text(e, 400, "{\"error\":\"Cross-case isolation violation: evidence " + evidenceId + " does not belong to case " + caseId + "\"}", "application/json");
                        return;
                    }

                    String oldRel = dao.getExistingRelationship(hypothesisId, evidenceId);
                    String notes = f.getOrDefault("notes", "");
                    dao.upsertRelationship(hypothesisId, evidenceId, validRel, notes, actor);

                    audit.record(actor, "HYPOTHESIS_RELATIONSHIP_CHANGED", "HYPOTHESIS", String.valueOf(hypothesisId),
                            "CASE_ID=" + caseId + "; evidence=" + evidenceId + "; from=" + oldRel + "; to=" + validRel + "; notes=" + notes);

                    HttpUtil.text(e, 200, "{\"ok\":true,\"relationshipType\":\"" + validRel + "\"}", "application/json");
                    return;
                }

                // Create hypothesis
                String code = f.get("code");
                String title = f.get("title");
                String desc = f.getOrDefault("description", "");
                if (code == null || code.isBlank() || title == null || title.isBlank()) {
                    HttpUtil.text(e, 400, "{\"error\":\"code and title are required\"}", "application/json");
                    return;
                }

                long id = dao.createHypothesis(caseId, code, title, desc, actor);
                audit.record(actor, "HYPOTHESIS_CREATED", "HYPOTHESIS", String.valueOf(id),
                        "CASE_ID=" + caseId + "; code=" + code + "; title=" + title);

                HttpUtil.text(e, 201, "{\"id\":" + id + ",\"code\":\"" + q(code) + "\"}", "application/json");
                return;
            }

            if ("PATCH".equalsIgnoreCase(method)) {
                long id = Long.parseLong(f.getOrDefault("id", "0"));
                String status = f.get("status");
                String validStatus = valid(status != null ? status.toUpperCase() : null,
                        new String[]{"ACTIVE", "UNDER_REVIEW", "RETAINED", "REJECTED_BY_INVESTIGATOR", "INACTIVE"}, null);
                if (id <= 0 || validStatus == null) {
                    HttpUtil.text(e, 400, "{\"error\":\"valid id and status are required\"}", "application/json");
                    return;
                }

                dao.updateStatus(id, validStatus);
                audit.record(actor, "HYPOTHESIS_STATUS_CHANGED", "HYPOTHESIS", String.valueOf(id),
                        "CASE_ID=" + caseId + "; status=" + validStatus);

                HttpUtil.text(e, 200, "{\"id\":" + id + ",\"status\":\"" + q(validStatus) + "\"}", "application/json");
                return;
            }

            HttpUtil.text(e, 405, "{\"error\":\"Method not allowed\"}", "application/json");
        } catch (Exception ex) {
            ex.printStackTrace();
            HttpUtil.text(e, 500, "{\"error\":\"Unable to process hypothesis request\"}", "application/json");
        }
    }

    private void handleGet(HttpExchange e, String caseId) throws Exception {
        List<Hypothesis> hypotheses = dao.findByCaseId(caseId);
        List<HypothesisEvidence> relationships = dao.getRelationshipsForCase(caseId);

        StringBuilder j = new StringBuilder("{\"caseId\":\"").append(q(caseId)).append("\",");
        j.append("\"disclaimer\":\"ACH is an analytic technique to evaluate evidence diagnostic value and mitigate cognitive confirmation bias. It does not determine legal guilt or substitute for human judicial judgment.\",");

        j.append("\"hypotheses\":[");
        for (int i = 0; i < hypotheses.size(); i++) {
            Hypothesis h = hypotheses.get(i);
            if (i > 0) j.append(',');
            j.append("{\"id\":").append(h.getId())
             .append(",\"caseId\":\"").append(q(h.getCaseId()))
             .append("\",\"hypothesisCode\":\"").append(q(h.getHypothesisCode()))
             .append("\",\"title\":\"").append(q(h.getTitle()))
             .append("\",\"description\":\"").append(q(h.getDescription()))
             .append("\",\"status\":\"").append(q(h.getStatus()))
             .append("\",\"createdBy\":\"").append(q(h.getCreatedBy()))
             .append("\",\"createdAt\":\"").append(q(h.getCreatedAt()))
             .append("\",\"updatedAt\":\"").append(q(h.getUpdatedAt()))
             .append("\",\"contradictionCount\":").append(h.getContradictionCount())
             .append(",\"supportCount\":").append(h.getSupportCount())
             .append(",\"contextCount\":").append(h.getContextCount())
             .append(",\"unassessedCount\":").append(h.getUnassessedCount())
             .append('}');
        }
        j.append("],\"evidence\":[");

        // Load evidence items for case
        String sql = "SELECT id, description, collection_date, current_custodian, file_path, file_hash_sha256, hash_algorithm, hash_verified_at, file_size_bytes FROM evidence WHERE case_id = ? ORDER BY id ASC";
        try (Connection c = Database.connect(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, caseId);
            try (ResultSet r = p.executeQuery()) {
                boolean first = true;
                while (r.next()) {
                    if (!first) j.append(',');
                    first = false;
                    String fPath = r.getString("file_path");
                    String fName = null;
                    if (fPath != null && !fPath.isBlank()) {
                        fName = java.nio.file.Path.of(fPath).getFileName().toString();
                    }
                    Number sizeNum = (Number) r.getObject("file_size_bytes");
                    Long size = sizeNum != null ? sizeNum.longValue() : null;

                    j.append("{\"id\":\"").append(q(r.getString("id")))
                     .append("\",\"description\":\"").append(q(r.getString("description")))
                     .append("\",\"collectionDate\":\"").append(q(r.getString("collection_date")))
                     .append("\",\"currentCustodian\":\"").append(q(r.getString("current_custodian")))
                     .append("\",\"fileName\":").append(fName != null ? "\"" + q(fName) + "\"" : "null")
                     .append(",\"fileSizeBytes\":").append(size != null ? size : "null")
                     .append(",\"fileHash\":\"").append(q(r.getString("file_hash_sha256")))
                     .append("\",\"hashAlgorithm\":\"").append(q(r.getString("hash_algorithm")))
                     .append("\",\"hashVerifiedAt\":\"").append(q(r.getString("hash_verified_at")))
                     .append("\"}");
                }
            }
        }

        j.append("],\"relationships\":[");
        for (int i = 0; i < relationships.size(); i++) {
            HypothesisEvidence he = relationships.get(i);
            if (i > 0) j.append(',');
            j.append("{\"id\":").append(he.getId())
             .append(",\"hypothesisId\":").append(he.getHypothesisId())
             .append(",\"evidenceId\":\"").append(q(he.getEvidenceId()))
             .append("\",\"relationshipType\":\"").append(q(he.getRelationshipType()))
             .append("\",\"notes\":\"").append(q(he.getNotes()))
             .append("\",\"createdBy\":\"").append(q(he.getCreatedBy()))
             .append("\",\"updatedAt\":\"").append(q(he.getUpdatedAt()))
             .append('}');
        }
        j.append("]}");

        HttpUtil.text(e, 200, j.toString(), "application/json");
    }

    private UserSession session(HttpExchange e) {
        String t = HttpUtil.cookie(e, "CCECS_SESSION");
        return t == null ? null : sessions.get(t).orElse(null);
    }

    private static String valid(String v, String[] allowed, String defaultVal) {
        if (v == null || v.isBlank()) return defaultVal;
        for (String a : allowed) {
            if (a.equalsIgnoreCase(v)) return a;
        }
        return defaultVal;
    }

    private static String param(String raw, String name) {
        if (raw == null) return null;
        for (String pair : raw.split("&")) {
            String[] p = pair.split("=", 2);
            if (p.length == 2 && p[0].equals(name)) return java.net.URLDecoder.decode(p[1], java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String q(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static boolean hypothesisBelongsToCase(long hypothesisId, String caseId) {
        try (Connection c = Database.connect();
             PreparedStatement p = c.prepareStatement("SELECT 1 FROM investigation_hypotheses WHERE id = ? AND case_id = ?")) {
            p.setLong(1, hypothesisId);
            p.setString(2, caseId);
            try (ResultSet r = p.executeQuery()) {
                return r.next();
            }
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean evidenceBelongsToCase(String evidenceId, String caseId) {
        try (Connection c = Database.connect();
             PreparedStatement p = c.prepareStatement("SELECT 1 FROM evidence WHERE id = ? AND case_id = ?")) {
            p.setString(1, evidenceId);
            p.setString(2, caseId);
            try (ResultSet r = p.executeQuery()) {
                return r.next();
            }
        } catch (Exception ex) {
            return false;
        }
    }
}
