import java.sql.*;
import java.util.*;

/** Persistent audit trail for investigation accountability. */
public final class AuditLogDao {
    public long record(String userId,String action,String entityType,String entityId,String details)throws SQLException{
        String q="INSERT INTO audit_logs(user_id,action,entity_type,entity_id,details) VALUES(?,?,?,?,?)";
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement(q,Statement.RETURN_GENERATED_KEYS)){
            p.setString(1,userId);p.setString(2,action);p.setString(3,entityType);p.setString(4,entityId);p.setString(5,details);p.executeUpdate();
            try(ResultSet r=p.getGeneratedKeys()){return r.next()?r.getLong(1):0;}
        }
    }
    public List<AuditRow> findByCase(String caseId)throws SQLException{
        String q="SELECT a.id,a.timestamp,a.action,a.entity_type,a.entity_id,a.details,a.user_id,COALESCE(u.username,'System / unknown') actor " +
                  "FROM audit_logs a LEFT JOIN users u ON u.id=a.user_id " +
                  "WHERE (a.entity_type='CASE' AND a.entity_id=?) OR " +
                  "(a.entity_type IN ('PERSON','EVIDENCE','EVENT','TASK','FINDING','RELATIONSHIP','REPORT') AND a.entity_id IN ("+
                  "SELECT entity_id FROM audit_logs WHERE entity_type='CASE' AND entity_id=?)) " +
                  "OR (a.details LIKE ?) ORDER BY a.timestamp DESC,a.id DESC";
        // The second/third conditions are deliberately conservative; most entries carry the case id in details.
        q="SELECT a.id,a.timestamp,a.action,a.entity_type,a.entity_id,a.details,a.user_id,COALESCE(u.username,'System / unknown') actor " +
          "FROM audit_logs a LEFT JOIN users u ON u.id=a.user_id WHERE (a.entity_type='CASE' AND a.entity_id=?) OR a.details LIKE ? ORDER BY a.timestamp DESC,a.id DESC";
        List<AuditRow> out=new ArrayList<>();
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement(q)){p.setString(1,caseId);p.setString(2,"%CASE_ID="+caseId+"%");try(ResultSet r=p.executeQuery()){while(r.next())out.add(new AuditRow(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8)));}}
        return out;
    }
    public static final class AuditRow {
        public final long id;
        public final String timestamp, action, entityType, entityId, details, userId, actor;
        public AuditRow(long id, String timestamp, String action, String entityType, String entityId, String details, String userId, String actor) {
            this.id = id; this.timestamp = timestamp; this.action = action; this.entityType = entityType;
            this.entityId = entityId; this.details = details; this.userId = userId; this.actor = actor;
        }
        public long id() { return id; }
        public String timestamp() { return timestamp; }
        public String action() { return action; }
        public String entityType() { return entityType; }
        public String entityId() { return entityId; }
        public String details() { return details; }
        public String userId() { return userId; }
        public String actor() { return actor; }
    }
}
