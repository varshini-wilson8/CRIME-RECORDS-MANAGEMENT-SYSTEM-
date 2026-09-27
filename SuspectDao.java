import java.sql.*;import java.util.*;
/** Read model exposing database-derived repeat-offender links. */
public final class SuspectDao {
 public List<String[]> rows()throws SQLException{List<String[]> r=new ArrayList<>();String q="SELECT s.id,s.name,s.risk_level,s.repeat_offender,COUNT(cs.case_id) links FROM suspects s LEFT JOIN case_suspects cs ON cs.suspect_id=s.id GROUP BY s.id ORDER BY links DESC,s.name";try(Connection c=Database.connect();Statement st=c.createStatement();ResultSet x=st.executeQuery(q)){while(x.next())r.add(new String[]{x.getString(1),x.getString(2),x.getString(3),x.getInt(4)==1?"Yes":"No",String.valueOf(x.getInt(5))});}return r;}
}
