import java.sql.*;
import java.util.*;
import model.Hypothesis;
import model.HypothesisEvidence;

public final class HypothesisDao {

    public List<Hypothesis> findByCaseId(String caseId) throws SQLException {
        List<Hypothesis> list = new ArrayList<>();
        String sql = "SELECT h.id, h.case_id, h.hypothesis_code, h.title, h.description, h.status, " +
                "COALESCE(h.created_by, '') created_by, h.created_at, h.updated_at, " +
                "SUM(CASE WHEN he.relationship_type = 'CONTRADICTS' THEN 1 ELSE 0 END) AS contradiction_cnt, " +
                "SUM(CASE WHEN he.relationship_type = 'SUPPORTS' THEN 1 ELSE 0 END) AS support_cnt, " +
                "SUM(CASE WHEN he.relationship_type = 'CONTEXT' THEN 1 ELSE 0 END) AS context_cnt, " +
                "SUM(CASE WHEN he.relationship_type = 'UNASSESSED' THEN 1 ELSE 0 END) AS unassessed_cnt " +
                "FROM investigation_hypotheses h " +
                "LEFT JOIN hypothesis_evidence he ON he.hypothesis_id = h.id " +
                "WHERE h.case_id = ? " +
                "GROUP BY h.id " +
                "ORDER BY h.hypothesis_code ASC";

        try (Connection c = Database.connect(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, caseId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    list.add(new Hypothesis(
                            r.getLong("id"),
                            r.getString("case_id"),
                            r.getString("hypothesis_code"),
                            r.getString("title"),
                            r.getString("description"),
                            r.getString("status"),
                            r.getString("created_by"),
                            r.getString("created_at"),
                            r.getString("updated_at"),
                            r.getInt("contradiction_cnt"),
                            r.getInt("support_cnt"),
                            r.getInt("context_cnt"),
                            r.getInt("unassessed_cnt")
                    ));
                }
            }
        }
        return list;
    }

    public List<HypothesisEvidence> getRelationshipsForCase(String caseId) throws SQLException {
        List<HypothesisEvidence> list = new ArrayList<>();
        String sql = "SELECT he.id, he.hypothesis_id, he.evidence_id, he.relationship_type, " +
                "COALESCE(he.notes, '') notes, COALESCE(he.created_by, '') created_by, he.updated_at " +
                "FROM hypothesis_evidence he " +
                "JOIN investigation_hypotheses h ON h.id = he.hypothesis_id " +
                "WHERE h.case_id = ? " +
                "ORDER BY he.hypothesis_id, he.evidence_id";

        try (Connection c = Database.connect(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, caseId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    list.add(new HypothesisEvidence(
                            r.getLong("id"),
                            r.getLong("hypothesis_id"),
                            r.getString("evidence_id"),
                            r.getString("relationship_type"),
                            r.getString("notes"),
                            r.getString("created_by"),
                            r.getString("updated_at")
                    ));
                }
            }
        }
        return list;
    }

    public long createHypothesis(String caseId, String code, String title, String description, String createdBy) throws SQLException {
        String sql = "INSERT INTO investigation_hypotheses(case_id, hypothesis_code, title, description, status, created_by) " +
                "VALUES(?,?,?,?, 'ACTIVE', ?)";
        try (Connection c = Database.connect(); PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, caseId);
            p.setString(2, code.toUpperCase().trim());
            p.setString(3, title.trim());
            p.setString(4, description != null ? description.trim() : "");
            p.setString(5, createdBy);
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) {
                if (r.next()) return r.getLong(1);
            }
        }
        return -1;
    }

    public void updateStatus(long hypothesisId, String status) throws SQLException {
        String sql = "UPDATE investigation_hypotheses SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection c = Database.connect(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, status);
            p.setLong(2, hypothesisId);
            p.executeUpdate();
        }
    }

    public String getExistingRelationship(long hypothesisId, String evidenceId) throws SQLException {
        String sql = "SELECT relationship_type FROM hypothesis_evidence WHERE hypothesis_id = ? AND evidence_id = ?";
        try (Connection c = Database.connect(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, hypothesisId);
            p.setString(2, evidenceId);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) return r.getString(1);
            }
        }
        return "UNASSESSED";
    }

    public void upsertRelationship(long hypothesisId, String evidenceId, String relType, String notes, String updatedBy) throws SQLException {
        String sql = "INSERT INTO hypothesis_evidence(hypothesis_id, evidence_id, relationship_type, notes, created_by, updated_at) " +
                "VALUES(?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT(hypothesis_id, evidence_id) DO UPDATE SET " +
                "relationship_type = excluded.relationship_type, " +
                "notes = excluded.notes, " +
                "updated_at = CURRENT_TIMESTAMP";

        try (Connection c = Database.connect(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, hypothesisId);
            p.setString(2, evidenceId);
            p.setString(3, relType);
            p.setString(4, notes != null ? notes : "");
            p.setString(5, updatedBy);
            p.executeUpdate();
        }
    }
}
