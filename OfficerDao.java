import java.sql.*;
import java.util.*;
import model.Officer;

public final class OfficerDao {
    public List<Officer> findAll() throws SQLException {List<Officer> result=new ArrayList<>();String sql="SELECT o.*,COUNT(c.id) workload FROM officers o LEFT JOIN cases c ON c.assigned_officer_id=o.id AND c.status NOT IN ('CLOSED','COLD') GROUP BY o.id ORDER BY workload,o.name";try(Connection c=Database.connect();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next())result.add(new Officer(r.getString("id"),r.getString("name"),r.getString("contact"),r.getString("badge_number"),r.getInt("workload")));}return result;}
    public void save(Officer o)throws SQLException{try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("INSERT INTO officers(id,name,contact,badge_number) VALUES(?,?,?,?)")){p.setString(1,o.getId());p.setString(2,o.getName());p.setString(3,o.getContact());p.setString(4,o.getBadgeNumber());p.executeUpdate();}}
    public Officer leastLoaded() throws SQLException { List<Officer> officers=findAll(); return officers.isEmpty()?null:officers.get(0); }
    public Officer findById(String id)throws SQLException{for(Officer o:findAll())if(o.getId().equalsIgnoreCase(id))return o;return null;}
}
