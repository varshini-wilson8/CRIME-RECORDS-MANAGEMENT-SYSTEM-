import java.sql.*;
import java.util.*;
import model.PersonRecord;

public final class PersonDao {
    public List<PersonRecord> findByCaseId(String caseId) throws SQLException {
        List<PersonRecord> out=new ArrayList<>();
        String sql="SELECT p.id,p.first_name,p.last_name,p.date_of_birth,p.phone,p.email,p.address,p.physical_description,cp.role,cp.notes,"
          +"(SELECT COUNT(DISTINCT cp2.case_id) FROM case_persons cp2 WHERE cp2.person_id=p.id) linked_cases,"
          +"(SELECT COUNT(*) FROM case_events ce WHERE ce.person_id=p.id) related_events,"
          +"(SELECT COUNT(DISTINCT cv.vehicle_id) FROM case_vehicles cv JOIN vehicles v ON v.id=cv.vehicle_id WHERE v.owner_person_id=p.id OR cv.case_id IN (SELECT case_id FROM case_persons WHERE person_id=p.id)) linked_vehicles,"
          +"(SELECT group_concat(c2.id || ' — ' || c2.title, ' | ') FROM (SELECT DISTINCT c.id,c.title FROM case_persons cp3 JOIN cases c ON c.id=cp3.case_id WHERE cp3.person_id=p.id ORDER BY c.id) c2) linked_case_details "
          +"FROM case_persons cp JOIN persons p ON p.id=cp.person_id WHERE cp.case_id=? ORDER BY CASE cp.role WHEN 'SUSPECT' THEN 1 WHEN 'PERSON_OF_INTEREST' THEN 2 WHEN 'VICTIM' THEN 3 WHEN 'WITNESS' THEN 4 ELSE 5 END,p.last_name,p.first_name";
        try(Connection c=Database.connect();PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,caseId);try(ResultSet r=ps.executeQuery()){while(r.next()) out.add(new PersonRecord(r.getString("id"),r.getString("first_name"),r.getString("last_name"),r.getString("date_of_birth"),r.getString("phone"),r.getString("email"),r.getString("address"),r.getString("physical_description"),r.getString("role"),r.getString("notes"),r.getInt("linked_cases"),r.getInt("related_events"),r.getInt("linked_vehicles"),r.getString("linked_case_details")));}}
        return out;
    }
}
