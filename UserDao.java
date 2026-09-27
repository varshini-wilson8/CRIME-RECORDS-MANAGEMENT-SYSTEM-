import java.sql.*;
import java.util.Optional;

public final class UserDao {
    public Optional<User> authenticate(String username,char[] password) throws SQLException {
        String sql="SELECT id,username,password_hash,officer_id,role FROM users WHERE username=?";
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,username);try(ResultSet r=p.executeQuery()){if(!r.next()||!PasswordHasher.verify(password,r.getString("password_hash")))return Optional.empty();return Optional.of(map(r));}}
    }
    public void create(String id,String username,char[] password,String officerId,Role role) throws SQLException {
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("INSERT INTO users(id,username,password_hash,officer_id,role) VALUES(?,?,?,?,?)")){p.setString(1,id);p.setString(2,username);p.setString(3,PasswordHasher.hash(password));p.setString(4,officerId);p.setString(5,role.name());p.executeUpdate();}
    }
    public void createDefaultAdminIfMissing() throws SQLException { if(!exists("admin")) create("U-ADMIN","admin","ChangeMe!2026".toCharArray(),null,Role.ADMIN); }
    public void createDefaultUsersIfMissing() throws SQLException {
        createDefaultAdminIfMissing();
        if (!exists("commander")) create("U-CMD-001","commander","ChangeMe!2026".toCharArray(),"O1",Role.COMMAND_OFFICER);
        if (!exists("fieldagent")) create("U-FLD-001","fieldagent","ChangeMe!2026".toCharArray(),"O2",Role.FIELD_AGENT);
    }
    public boolean changePassword(String userId,char[] current,char[] replacement)throws SQLException {
        try(Connection c=Database.connect();PreparedStatement q=c.prepareStatement("SELECT password_hash FROM users WHERE id=?")){q.setString(1,userId);try(ResultSet r=q.executeQuery()){if(!r.next()||!PasswordHasher.verify(current,r.getString(1)))return false;}}
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("UPDATE users SET password_hash=? WHERE id=?")){p.setString(1,PasswordHasher.hash(replacement));p.setString(2,userId);return p.executeUpdate()==1;}
    }
    private boolean exists(String username)throws SQLException{try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("SELECT 1 FROM users WHERE username=?")){p.setString(1,username);try(ResultSet r=p.executeQuery()){return r.next();}}}
    private User map(ResultSet r)throws SQLException{return new User(r.getString("id"),r.getString("username"),r.getString("officer_id"),Role.valueOf(r.getString("role")));}
}
