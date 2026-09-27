import com.sun.net.httpserver.*;
import java.io.IOException;
import java.util.*;
import model.CaseEvent;
import model.TimelineGap;

public final class CaseEventApiHandler implements HttpHandler {
    private final CaseEventDao events;
    private final CaseDao cases;
    private final SessionManager sessions;
    public CaseEventApiHandler(CaseEventDao events, CaseDao cases, SessionManager sessions){this.events=events;this.cases=cases;this.sessions=sessions;}

    public void handle(HttpExchange e) throws IOException {
        try {
            if(!e.getRequestMethod().equals("GET")) { HttpUtil.text(e,405,"{\"error\":\"Method not allowed\"}","application/json"); return; }
            String query=e.getRequestURI().getRawQuery();
            String caseId=param(query,"caseId");
            if(caseId==null || caseId.isBlank()) { HttpUtil.text(e,400,"{\"error\":\"caseId is required\"}","application/json"); return; }
            boolean exists=cases.findAll().stream().anyMatch(c->c.getCaseId().equals(caseId));
            if(!exists){HttpUtil.text(e,404,"{\"error\":\"Case not found\"}","application/json");return;}

            List<CaseEvent> list = events.findByCaseId(caseId);
            List<TimelineGap> gaps = events.detectGaps(list);

            String format = param(query, "format");
            if ("array".equalsIgnoreCase(format)) {
                StringBuilder j = new StringBuilder("[");
                for (CaseEvent x : list) {
                    if (j.length() > 1) j.append(',');
                    appendEventJson(j, x);
                }
                HttpUtil.text(e, 200, j.append(']').toString(), "application/json");
                return;
            }

            StringBuilder j = new StringBuilder("{\"events\":[");
            for (CaseEvent x : list) {
                if (j.charAt(j.length() - 1) != '[') j.append(',');
                appendEventJson(j, x);
            }
            j.append("],\"gaps\":[");
            for (TimelineGap g : gaps) {
                if (j.charAt(j.length() - 1) != '[') j.append(',');
                j.append("{\"fromTime\":\"").append(q(g.getFromTime()))
                 .append("\",\"toTime\":\"").append(q(g.getToTime()))
                 .append("\",\"durationMinutes\":").append(g.getDurationMinutes())
                 .append(",\"windowType\":\"").append(q(g.getWindowType()))
                 .append("\",\"thresholdMinutes\":").append(g.getThresholdMinutes())
                 .append(",\"label\":\"").append(q(g.getLabel()))
                 .append("\",\"disclaimer\":\"").append(q(g.getDisclaimer()))
                 .append("\",\"suggestedTaskTitle\":\"").append(q(g.getSuggestedTaskTitle()))
                 .append("\",\"suggestedTaskDescription\":\"").append(q(g.getSuggestedTaskDescription()))
                 .append("\",\"suggestedTaskPriority\":\"").append(q(g.getSuggestedTaskPriority()))
                 .append("\"}");
            }
            j.append("],\"thresholds\":{\"incidentReviewMinutes\":")
             .append(CaseEventDao.INCIDENT_REVIEW_GAP_THRESHOLD_MINUTES)
             .append(",\"generalMinutes\":")
             .append(CaseEventDao.GENERAL_GAP_THRESHOLD_MINUTES)
             .append("}}");

            HttpUtil.text(e,200,j.toString(),"application/json");
        } catch(Exception ex) {
            ex.printStackTrace();
            HttpUtil.text(e,500,"{\"error\":\"Unable to load case timeline\"}","application/json");
        }
    }

    private static void appendEventJson(StringBuilder j, CaseEvent x) {
        j.append("{\"id\":").append(x.getId())
         .append(",\"caseId\":\"").append(q(x.getCaseId()))
         .append("\",\"eventType\":\"").append(q(x.getEventType()))
         .append("\",\"title\":\"").append(q(x.getTitle()))
         .append("\",\"description\":\"").append(q(x.getDescription()))
         .append("\",\"eventTime\":\"").append(q(x.getEventTime()))
         .append("\",\"location\":\"").append(q(x.getLocationName()))
         .append("\",\"person\":\"").append(q(x.getPersonName()))
         .append("\",\"evidenceId\":\"").append(q(x.getEvidenceId()))
         .append("\",\"source\":\"").append(q(x.getSource()))
         .append("\",\"verificationStatus\":\"").append(q(x.getVerificationStatus()))
         .append("\",\"truthLevel\":\"").append(q(x.getTruthLevel()))
         .append("\"}");
    }

    private static String param(String raw,String name){
        if(raw==null)return null;
        for(String pair:raw.split("&")){String[] p=pair.split("=",2);if(p.length==2 && p[0].equals(name))return java.net.URLDecoder.decode(p[1],java.nio.charset.StandardCharsets.UTF_8);}
        return null;
    }
    private static String q(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
}
