import com.sun.net.httpserver.*;
import java.io.IOException;
import java.util.*;
import model.PersonRecord;

public final class PersonApiHandler implements HttpHandler {
    private final PersonDao people; private final CaseDao cases;
    public PersonApiHandler(PersonDao people,CaseDao cases){this.people=people;this.cases=cases;}
    public void handle(HttpExchange e)throws IOException{
        try{
            if(!"GET".equals(e.getRequestMethod())){HttpUtil.text(e,405,"{\"error\":\"Method not allowed\"}","application/json");return;}
            String caseId=param(e.getRequestURI().getRawQuery(),"caseId");
            if(caseId==null||caseId.isBlank()){HttpUtil.text(e,400,"{\"error\":\"caseId is required\"}","application/json");return;}
            boolean exists=cases.findAll().stream().anyMatch(c->c.getCaseId().equals(caseId));
            if(!exists){HttpUtil.text(e,404,"{\"error\":\"Case not found\"}","application/json");return;}
            StringBuilder j=new StringBuilder("[");
            for(PersonRecord p:people.findByCaseId(caseId)){
                if(j.length()>1)j.append(',');
                j.append("{\"id\":\"").append(q(p.getId())).append("\",\"name\":\"").append(q(p.getFullName())).append("\",\"firstName\":\"").append(q(p.getFirstName())).append("\",\"lastName\":\"").append(q(p.getLastName())).append("\",\"dateOfBirth\":\"").append(q(p.getDateOfBirth())).append("\",\"phone\":\"").append(q(p.getPhone())).append("\",\"email\":\"").append(q(p.getEmail())).append("\",\"address\":\"").append(q(p.getAddress())).append("\",\"physicalDescription\":\"").append(q(p.getPhysicalDescription())).append("\",\"role\":\"").append(q(p.getRole())).append("\",\"notes\":\"").append(q(p.getNotes())).append("\",\"linkedCases\":").append(p.getLinkedCases()).append(",\"relatedEvents\":").append(p.getRelatedEvents()).append(",\"linkedVehicles\":").append(p.getLinkedVehicles()).append(",\"linkedCaseDetails\":\"").append(q(p.getLinkedCaseDetails())).append("\"}");
            }
            HttpUtil.text(e,200,j.append(']').toString(),"application/json");
        }catch(Exception ex){HttpUtil.text(e,500,"{\"error\":\"Unable to load people intelligence\"}","application/json");}
    }
    private static String param(String raw,String name){if(raw==null)return null;for(String pair:raw.split("&")){String[] p=pair.split("=",2);if(p.length==2&&p[0].equals(name))return java.net.URLDecoder.decode(p[1],java.nio.charset.StandardCharsets.UTF_8);}return null;}
    private static String q(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
}
