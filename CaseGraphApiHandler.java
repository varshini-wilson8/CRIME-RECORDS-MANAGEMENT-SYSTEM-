import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/** Investigation relationship graph. Derived links are presented as context for investigator review. */
public final class CaseGraphApiHandler implements HttpHandler {
    private final CaseDao cases;
    public CaseGraphApiHandler(CaseDao cases){this.cases=cases;}

    public void handle(HttpExchange e) throws IOException {
        try {
            if(!"GET".equals(e.getRequestMethod())){HttpUtil.text(e,405,"{\"error\":\"Method not allowed\"}","application/json");return;}
            String caseId=param(e.getRequestURI().getRawQuery(),"caseId");
            if(caseId==null||caseId.isBlank()){HttpUtil.text(e,400,"{\"error\":\"caseId is required\"}","application/json");return;}
            boolean exists=cases.findAll().stream().anyMatch(c->c.getCaseId().equals(caseId));
            if(!exists){HttpUtil.text(e,404,"{\"error\":\"Case not found\"}","application/json");return;}
            List<Node> nodes=new ArrayList<>(); List<Edge> edges=new ArrayList<>(); Set<String> seen=new HashSet<>(); Set<String> edgeSeen=new HashSet<>();
            addNode(nodes,seen,"CASE",caseId,"CASE "+caseId,caseId);
            try(Connection c=Database.connect()){
                // People directly linked to this case, plus their other cases.
                String sql="SELECT cp.person_id,p.first_name,p.last_name,cp.role FROM case_persons cp JOIN persons p ON p.id=cp.person_id WHERE cp.case_id=?";
                try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,caseId);try(ResultSet r=ps.executeQuery()){while(r.next()){
                    String pid=r.getString(1), name=(r.getString(2)+" "+Optional.ofNullable(r.getString(3)).orElse("")).trim();
                    addNode(nodes,seen,"PERSON",pid,name.isBlank()?pid:name,r.getString(3)); addEdge(edges,edgeSeen,"CASE",caseId,"PERSON",pid,r.getString(4));
                    try(PreparedStatement q=c.prepareStatement("SELECT cp.case_id,ca.title,cp.role FROM case_persons cp JOIN cases ca ON ca.id=cp.case_id WHERE cp.person_id=? AND cp.case_id<>? ORDER BY ca.id")){q.setString(1,pid);q.setString(2,caseId);try(ResultSet x=q.executeQuery()){while(x.next()){
                        String cid=x.getString(1); addNode(nodes,seen,"CASE",cid,"CASE "+cid,cid); addEdge(edges,edgeSeen,"PERSON",pid,"CASE",cid,x.getString(3));
                    }}}
                }}}
                // Evidence linked to case.
                try(PreparedStatement ps=c.prepareStatement("SELECT id,description FROM evidence WHERE case_id=? ORDER BY id")){ps.setString(1,caseId);try(ResultSet r=ps.executeQuery()){while(r.next()){String id=r.getString(1);addNode(nodes,seen,"EVIDENCE",id,"Evidence "+id,r.getString(2));addEdge(edges,edgeSeen,"CASE",caseId,"EVIDENCE",id,"HAS EVIDENCE");}}}
                // Locations linked to case.
                try(PreparedStatement ps=c.prepareStatement("SELECT cl.location_id,l.name,cl.relationship_type FROM case_locations cl JOIN locations l ON l.id=cl.location_id WHERE cl.case_id=?")){ps.setString(1,caseId);try(ResultSet r=ps.executeQuery()){while(r.next()){String id=r.getString(1);addNode(nodes,seen,"LOCATION",id,r.getString(2),r.getString(2));addEdge(edges,edgeSeen,"CASE",caseId,"LOCATION",id,r.getString(3));}}}
                // Vehicles linked to case, and owner.
                try(PreparedStatement ps=c.prepareStatement("SELECT cv.vehicle_id,v.registration_number,v.make,v.model,cv.relationship_type,v.owner_person_id FROM case_vehicles cv JOIN vehicles v ON v.id=cv.vehicle_id WHERE cv.case_id=?")){ps.setString(1,caseId);try(ResultSet r=ps.executeQuery()){while(r.next()){
                    String id=r.getString(1), label=r.getString(2); addNode(nodes,seen,"VEHICLE",id,label,r.getString(3)+" "+r.getString(4));addEdge(edges,edgeSeen,"CASE",caseId,"VEHICLE",id,r.getString(5));
                    if(r.getString(6)!=null){String owner=r.getString(6); String n=queryPersonName(c,owner);addNode(nodes,seen,"PERSON",owner,n,"");addEdge(edges,edgeSeen,"VEHICLE",id,"PERSON",owner,"OWNER");}
                }}}
                // Timeline events become investigation-context nodes; connect their referenced entities.
                String es="SELECT ce.id,ce.title,ce.location_id,ce.person_id,ce.evidence_id FROM case_events ce WHERE ce.case_id=? ORDER BY ce.event_time";
                try(PreparedStatement ps=c.prepareStatement(es)){ps.setString(1,caseId);try(ResultSet r=ps.executeQuery()){while(r.next()){
                    String id=String.valueOf(r.getLong(1));addNode(nodes,seen,"EVENT",id,"Event: "+r.getString(2),r.getString(2));addEdge(edges,edgeSeen,"CASE",caseId,"EVENT",id,"TIMELINE");
                    if(r.getString(3)!=null){String lid=r.getString(3);String label=queryLocationName(c,lid);addNode(nodes,seen,"LOCATION",lid,label,label);addEdge(edges,edgeSeen,"EVENT",id,"LOCATION",lid,"AT");}
                    if(r.getString(4)!=null){String pid=r.getString(4);String label=queryPersonName(c,pid);addNode(nodes,seen,"PERSON",pid,label,"");addEdge(edges,edgeSeen,"EVENT",id,"PERSON",pid,"INVOLVES");}
                    if(r.getString(5)!=null){String eid=r.getString(5);addNode(nodes,seen,"EVIDENCE",eid,"Evidence "+eid,"");addEdge(edges,edgeSeen,"EVENT",id,"EVIDENCE",eid,"REFERENCES");}
                }}}
                // Explicit relationship records are included when they touch the selected case or one of its nodes.
                Set<String> allowed=new HashSet<>();for(Node n:nodes)allowed.add(n.type+":"+n.id);
                try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT source_type,source_id,target_type,target_id,relationship_type,status,confidence,reason FROM entity_relationships")){while(r.next()){
                    String sType=r.getString(1),sId=r.getString(2),tType=r.getString(3),tId=r.getString(4);
                    if(allowed.contains(sType+":"+sId)||allowed.contains(tType+":"+tId)){
                        addNode(nodes,seen,sType,sId,labelFor(c,sType,sId),"");addNode(nodes,seen,tType,tId,labelFor(c,tType,tId),"");
                        String label=r.getString(5);if(r.getString(6)!=null&&!"CONFIRMED".equals(r.getString(6)))label+=" • "+r.getString(6).replace('_',' ');
                        addEdge(edges,edgeSeen,sType,sId,tType,tId,label);
                    }
                }}
            }
            StringBuilder j=new StringBuilder("{\"caseId\":\"").append(q(caseId)).append("\",\"nodes\":[");
            for(int i=0;i<nodes.size();i++){if(i>0)j.append(',');j.append(nodes.get(i).json());}
            j.append("],\"edges\":[");for(int i=0;i<edges.size();i++){if(i>0)j.append(',');j.append(edges.get(i).json());}j.append("]}");
            HttpUtil.text(e,200,j.toString(),"application/json");
        }catch(Exception ex){ex.printStackTrace();HttpUtil.text(e,500,"{\"error\":\"Unable to load case relationship graph\"}","application/json");}
    }
    private static String queryPersonName(Connection c,String id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT first_name,last_name FROM persons WHERE id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(r.next())return (r.getString(1)+" "+Optional.ofNullable(r.getString(2)).orElse("")).trim();}}return id;}
    private static String queryLocationName(Connection c,String id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT name FROM locations WHERE id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(r.next())return r.getString(1);}}return id;}
    private static String labelFor(Connection c,String type,String id)throws SQLException{switch(type){case"PERSON":return queryPersonName(c,id);case"LOCATION":return queryLocationName(c,id);case"EVIDENCE":return "Evidence "+id;case"VEHICLE":try(PreparedStatement p=c.prepareStatement("SELECT registration_number FROM vehicles WHERE id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(r.next())return r.getString(1);}}return id;case"CASE":return "CASE "+id;default:return type+" "+id;}}
    private static void addNode(List<Node> n,Set<String>s,String t,String id,String label,String detail){if(id==null||id.isBlank())return;if(s.add(t+":"+id))n.add(new Node(t,id,label,detail));}
    private static void addEdge(List<Edge> e,Set<String>s,String st,String si,String tt,String ti,String label){String k=st+":"+si+">"+tt+":"+ti+":"+label;if(s.add(k))e.add(new Edge(st+":"+si,tt+":"+ti,label));}
    private static String param(String raw,String name){if(raw==null)return null;for(String pair:raw.split("&")){String[]p=pair.split("=",2);if(p.length==2&&p[0].equals(name))return java.net.URLDecoder.decode(p[1],java.nio.charset.StandardCharsets.UTF_8);}return null;}
    private static String q(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
    private static final class Node {
        final String type, id, label, detail;
        Node(String type, String id, String label, String detail) {
            this.type = type; this.id = id; this.label = label; this.detail = detail;
        }
        String json() {
            return "{\"id\":\""+q(type+":"+id)+"\",\"type\":\""+q(type)+"\",\"label\":\""+q(label)+"\",\"detail\":\""+q(detail)+"\"}";
        }
    }
    private static final class Edge {
        final String source, target, label;
        Edge(String source, String target, String label) {
            this.source = source; this.target = target; this.label = label;
        }
        String json() {
            return "{\"source\":\""+q(source)+"\",\"target\":\""+q(target)+"\",\"label\":\""+q(label)+"\"}";
        }
    }
}
