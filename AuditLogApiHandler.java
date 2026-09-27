import com.sun.net.httpserver.*;import java.io.*;import java.util.*;

/** Read-only chronological audit/activity feed for a case. */
public final class AuditLogApiHandler implements HttpHandler{
 private final AuditLogDao audit=new AuditLogDao(); private final CaseDao cases;
 public AuditLogApiHandler(CaseDao c){cases=c;}
 public void handle(HttpExchange e)throws IOException{try{
   if(!"GET".equals(e.getRequestMethod())){HttpUtil.text(e,405,"{\"error\":\"Method not allowed\"}","application/json");return;}
   String caseId=param(e.getRequestURI().getRawQuery(),"caseId");if(caseId==null||caseId.isBlank()){HttpUtil.text(e,400,"{\"error\":\"caseId is required\"}","application/json");return;}
   if(cases.findAll().stream().noneMatch(c->c.getCaseId().equals(caseId))){HttpUtil.text(e,404,"{\"error\":\"Case not found\"}","application/json");return;}
   List<AuditLogDao.AuditRow> rows=audit.findByCase(caseId);StringBuilder j=new StringBuilder("{\"caseId\":\"").append(q(caseId)).append("\",\"total\":").append(rows.size()).append(",\"events\":[");
   for(int i=0;i<rows.size();i++){if(i>0)j.append(',');AuditLogDao.AuditRow r=rows.get(i);j.append("{\"id\":").append(r.id()).append(",\"timestamp\":\"").append(q(r.timestamp())).append("\",\"action\":\"").append(q(r.action())).append("\",\"entityType\":\"").append(q(r.entityType())).append("\",\"entityId\":\"").append(q(r.entityId())).append("\",\"details\":\"").append(q(r.details())).append("\",\"userId\":\"").append(q(r.userId())).append("\",\"actor\":\"").append(q(r.actor())).append("\"}");}
   j.append("]}");HttpUtil.text(e,200,j.toString(),"application/json");
 }catch(Exception ex){ex.printStackTrace();HttpUtil.text(e,500,"{\"error\":\"Unable to load audit activity\"}","application/json");}}
 private static String param(String raw,String name){if(raw==null)return null;for(String pair:raw.split("&")){String[]p=pair.split("=",2);if(p.length==2&&p[0].equals(name))return java.net.URLDecoder.decode(p[1],java.nio.charset.StandardCharsets.UTF_8);}return null;}
 private static String q(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
}
