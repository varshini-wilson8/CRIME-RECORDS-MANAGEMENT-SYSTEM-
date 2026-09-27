import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/** System-wide operational analytics for supervisors and command staff. */
public final class CommandCenterApiHandler implements HttpHandler {
    public void handle(HttpExchange e) throws IOException {
        if (!"GET".equals(e.getRequestMethod())) { HttpUtil.text(e,405,"{\"error\":\"Method not allowed\"}","application/json"); return; }
        try { HttpUtil.text(e,200,buildJson(),"application/json"); }
        catch(Exception ex){ ex.printStackTrace(); HttpUtil.text(e,500,"{\"error\":\"Unable to load command center analytics\"}","application/json"); }
    }

    private String buildJson() throws SQLException {
        try(Connection c=Database.connect()) {
            int total=count(c,"SELECT COUNT(*) FROM cases");
            int open=count(c,"SELECT COUNT(*) FROM cases WHERE UPPER(status) NOT IN ('CLOSED','ARCHIVED')");
            int critical=count(c,"SELECT COUNT(*) FROM cases WHERE UPPER(severity)='CRITICAL'");
            int high=count(c,"SELECT COUNT(*) FROM cases WHERE UPPER(severity)='HIGH'");
            int overdueTasks=count(c,"SELECT COUNT(*) FROM investigation_tasks WHERE status NOT IN ('COMPLETED','CANCELLED') AND due_date IS NOT NULL AND date(due_date)<date('now')");
            int openTasks=count(c,"SELECT COUNT(*) FROM investigation_tasks WHERE status NOT IN ('COMPLETED','CANCELLED')");
            int evidence=count(c,"SELECT COUNT(*) FROM evidence");
            int unverified=count(c,"SELECT COUNT(*) FROM case_events WHERE verification_status='UNVERIFIED'");
            int pendingReview=count(c,"SELECT COUNT(*) FROM entity_relationships WHERE status='PENDING_REVIEW'");
            int openFindings=count(c,"SELECT COUNT(*) FROM investigation_findings WHERE status IN ('OPEN','UNDER_REVIEW')");
            int auditEvents=count(c,"SELECT COUNT(*) FROM audit_logs");
            int activeOfficers=count(c,"SELECT COUNT(*) FROM officers");
            StringBuilder j=new StringBuilder("{");
            j.append("\"metrics\":{")
             .append("\"totalCases\":").append(total).append(',')
             .append("\"openCases\":").append(open).append(',')
             .append("\"criticalCases\":").append(critical).append(',')
             .append("\"highCases\":").append(high).append(',')
             .append("\"overdueTasks\":").append(overdueTasks).append(',')
             .append("\"openTasks\":").append(openTasks).append(',')
             .append("\"evidence\":").append(evidence).append(',')
             .append("\"unverifiedEvents\":").append(unverified).append(',')
             .append("\"pendingRelationships\":").append(pendingReview).append(',')
             .append("\"openFindings\":").append(openFindings).append(',')
             .append("\"auditEvents\":").append(auditEvents).append(',')
             .append("\"activeOfficers\":").append(activeOfficers).append("},");
            j.append("\"caseStatus\":").append(rows(c,"SELECT COALESCE(status,'UNKNOWN') label,COUNT(*) value FROM cases GROUP BY status ORDER BY value DESC"));
            j.append(",\"severity\":").append(rows(c,"SELECT COALESCE(severity,'UNKNOWN') label,COUNT(*) value FROM cases GROUP BY severity ORDER BY value DESC"));
            j.append(",\"monthlyCases\":").append(rows(c,"SELECT substr(opening_date,1,7) label,COUNT(*) value FROM cases GROUP BY substr(opening_date,1,7) ORDER BY label"));
            j.append(",\"officerWorkload\":").append(officerWorkload(c));
            j.append(",\"taskStatus\":").append(rows(c,"SELECT COALESCE(status,'UNKNOWN') label,COUNT(*) value FROM investigation_tasks GROUP BY status ORDER BY value DESC"));
            j.append(",\"recurringPeople\":").append(recurring(c,"SELECT p.id entityId,TRIM(p.first_name||' '||COALESCE(p.last_name,'')) label,COUNT(DISTINCT cp.case_id) value FROM persons p JOIN case_persons cp ON cp.person_id=p.id GROUP BY p.id HAVING COUNT(DISTINCT cp.case_id)>1 ORDER BY value DESC,label LIMIT 10"));
            j.append(",\"recurringLocations\":").append(recurring(c,"SELECT l.id entityId,l.name label,COUNT(DISTINCT cl.case_id) value FROM locations l JOIN case_locations cl ON cl.location_id=l.id GROUP BY l.id HAVING COUNT(DISTINCT cl.case_id)>1 ORDER BY value DESC,label LIMIT 10"));
            j.append(",\"recurringVehicles\":").append(recurring(c,"SELECT v.id entityId,v.registration_number label,COUNT(DISTINCT cv.case_id) value FROM vehicles v JOIN case_vehicles cv ON cv.vehicle_id=v.id GROUP BY v.id HAVING COUNT(DISTINCT cv.case_id)>1 ORDER BY value DESC,label LIMIT 10"));
            j.append(",\"topPriorityCases\":").append(topCases(c));
            j.append('}');
            return j.toString();
        }
    }
    private int count(Connection c,String q)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(q)){return r.next()?r.getInt(1):0;}}
    private String rows(Connection c,String q)throws SQLException{StringBuilder j=new StringBuilder("[");try(Statement s=c.createStatement();ResultSet r=s.executeQuery(q)){int i=0;while(r.next()){if(i++>0)j.append(',');j.append("{\"label\":\"").append(q(r.getString(1))).append("\",\"value\":").append(r.getInt(2)).append('}');}}return j.append(']').toString();}
    private String recurring(Connection c,String q)throws SQLException{StringBuilder j=new StringBuilder("[");try(Statement s=c.createStatement();ResultSet r=s.executeQuery(q)){int i=0;while(r.next()){if(i++>0)j.append(',');j.append("{\"entityId\":\"").append(q(r.getString(1))).append("\",\"label\":\"").append(q(r.getString(2))).append("\",\"cases\":").append(r.getInt(3)).append('}');}}return j.append(']').toString();}
    private String officerWorkload(Connection c)throws SQLException{String sql="SELECT o.id,o.name,COUNT(CASE WHEN UPPER(c.status) NOT IN ('CLOSED','ARCHIVED') THEN 1 END) openCases,COUNT(c.id) totalCases FROM officers o LEFT JOIN cases c ON c.assigned_officer_id=o.id GROUP BY o.id,o.name ORDER BY openCases DESC,o.name";StringBuilder j=new StringBuilder("[");try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){int i=0;while(r.next()){if(i++>0)j.append(',');j.append("{\"id\":\"").append(q(r.getString(1))).append("\",\"name\":\"").append(q(r.getString(2))).append("\",\"openCases\":").append(r.getInt(3)).append(",\"totalCases\":").append(r.getInt(4)).append('}');}}return j.append(']').toString();}
    private String topCases(Connection c)throws SQLException{String sql="SELECT id,title,severity,status,priority,opening_date FROM cases ORDER BY priority DESC LIMIT 8";StringBuilder j=new StringBuilder("[");try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){int i=0;while(r.next()){if(i++>0)j.append(',');j.append("{\"caseId\":\"").append(q(r.getString(1))).append("\",\"title\":\"").append(q(r.getString(2))).append("\",\"severity\":\"").append(q(r.getString(3))).append("\",\"status\":\"").append(q(r.getString(4))).append("\",\"priority\":").append(r.getInt(5)).append(",\"opened\":\"").append(q(r.getString(6))).append("\"}");}}return j.append(']').toString();}
    private static String q(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
}
