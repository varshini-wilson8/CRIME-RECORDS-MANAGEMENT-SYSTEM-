import com.sun.net.httpserver.*;import java.io.IOException;import java.sql.*;import java.time.*;import java.util.*;
public final class CasePriorityApiHandler implements HttpHandler{
 public void handle(HttpExchange e)throws IOException{try{String caseId=param(e.getRequestURI().getRawQuery(),"caseId");if(caseId==null||caseId.isBlank()){HttpUtil.text(e,400,"{\"error\":\"caseId is required\"}","application/json");return;}try(Connection c=Database.connect()){
  String sql="SELECT c.severity,c.status,c.opening_date,\n"+
   "(SELECT COUNT(*) FROM case_events x WHERE x.case_id=c.id) event_count,\n"+
   "(SELECT COUNT(*) FROM evidence x WHERE x.case_id=c.id) evidence_count,\n"+
   "(SELECT COUNT(*) FROM investigation_tasks x WHERE x.case_id=c.id AND x.status NOT IN ('COMPLETED','CANCELLED')) open_tasks,\n"+
   "(SELECT COUNT(*) FROM investigation_tasks x WHERE x.case_id=c.id AND x.status NOT IN ('COMPLETED','CANCELLED') AND x.due_date IS NOT NULL AND date(x.due_date)<date('now')) overdue_tasks,\n"+
   "(SELECT COUNT(*) FROM investigation_findings x WHERE x.case_id=c.id AND x.status NOT IN ('RESOLVED')) open_findings,\n"+
   "(SELECT COUNT(*) FROM investigation_findings x WHERE x.case_id=c.id AND x.status IN ('SUPPORTED','CONTRADICTED')) reviewed_findings,\n"+
   "(SELECT COUNT(*) FROM case_persons x WHERE x.case_id=c.id) people_count\n"+
   "FROM cases c WHERE c.id=?";
  try(PreparedStatement p=c.prepareStatement(sql)){p.setString(1,caseId);try(ResultSet r=p.executeQuery()){if(!r.next()){HttpUtil.text(e,404,"{\"error\":\"Case not found\"}","application/json");return;}
   String severity=r.getString("severity"),status=r.getString("status"),opened=r.getString("opening_date");int events=r.getInt("event_count"),evidence=r.getInt("evidence_count"),openTasks=r.getInt("open_tasks"),overdue=r.getInt("overdue_tasks"),openFindings=r.getInt("open_findings"),reviewed=r.getInt("reviewed_findings"),people=r.getInt("people_count");
    int severityPoints;
    if ("CRITICAL".equals(severity)) severityPoints = 40;
    else if ("HIGH".equals(severity)) severityPoints = 30;
    else if ("MEDIUM".equals(severity)) severityPoints = 20;
    else severityPoints = 10;
    long ageDays=0;try{ageDays=Math.max(0,java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(opened),LocalDate.now()));}catch(Exception ignored){}int agePoints=(int)Math.min(30,ageDays);int overduePoints=Math.min(20,overdue*5);int gapPoints=0;if(evidence==0)gapPoints+=5;if(events==0)gapPoints+=5;if(openFindings>0)gapPoints+=Math.min(10,openFindings*2);int score=Math.min(100,severityPoints+agePoints+overduePoints+gapPoints);
   int completed=Math.max(0, countTasks(c,caseId,"COMPLETED"));int totalTask= countTasks(c,caseId,"ALL");int progress=totalTask==0?0:(int)Math.round(completed*100.0/totalTask);
   StringBuilder j=new StringBuilder("{\"caseId\":\"").append(q(caseId)).append("\",\"score\":").append(score).append(",\"severity\":\"").append(q(severity)).append("\",\"status\":\"").append(q(status)).append("\",\"ageDays\":").append(ageDays).append(",\"taskProgress\":").append(progress).append(",\"metrics\":{");
   j.append("\"events\":").append(events).append(",\"evidence\":").append(evidence).append(",\"openTasks\":").append(openTasks).append(",\"overdueTasks\":").append(overdue).append(",\"openFindings\":").append(openFindings).append(",\"reviewedFindings\":").append(reviewed).append(",\"people\":").append(people).append("},\"contributors\":[");
   List<String> parts=new ArrayList<>();parts.add("{\"label\":\"Severity\",\"points\":"+severityPoints+",\"detail\":\""+q(pretty(severity))+" case classification\"}");if(agePoints>0)parts.add("{\"label\":\"Case age\",\"points\":"+agePoints+",\"detail\":\""+ageDays+" day(s) open\"}");if(overduePoints>0)parts.add("{\"label\":\"Overdue tasks\",\"points\":"+overduePoints+",\"detail\":\""+overdue+" overdue open task(s)\"}");if(gapPoints>0)parts.add("{\"label\":\"Open investigation gaps\",\"points\":"+gapPoints+",\"detail\":\""+openFindings+" open finding(s), "+evidence+" evidence item(s), "+events+" timeline event(s)\"}");j.append(String.join(",",parts)).append("]}");HttpUtil.text(e,200,j.toString(),"application/json");
  }}}
 }catch(Exception x){x.printStackTrace();HttpUtil.text(e,500,"{\"error\":\"Unable to calculate case priority\"}","application/json");}}
 private int countTasks(Connection c,String id,String status)throws SQLException{String q="ALL".equals(status)?"SELECT COUNT(*) FROM investigation_tasks WHERE case_id=?":"SELECT COUNT(*) FROM investigation_tasks WHERE case_id=? AND status=?";try(PreparedStatement p=c.prepareStatement(q)){p.setString(1,id);if(!"ALL".equals(status))p.setString(2,status);try(ResultSet r=p.executeQuery()){return r.next()?r.getInt(1):0;}}}
 private static String pretty(String s){if(s==null)return "Unknown";String x=s.replace('_',' ').toLowerCase(Locale.ROOT);return x.substring(0,1).toUpperCase(Locale.ROOT)+x.substring(1);}
 private static String param(String raw,String name){if(raw==null)return null;for(String pair:raw.split("&")){String[]p=pair.split("=",2);if(p.length==2&&p[0].equals(name))return java.net.URLDecoder.decode(p[1],java.nio.charset.StandardCharsets.UTF_8);}return null;}
 private static String q(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
}
