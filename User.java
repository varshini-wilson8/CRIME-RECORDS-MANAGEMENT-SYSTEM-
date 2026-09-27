/** Database-backed login identity; password hashes never leave the DAO. */
public final class User {
    private final String id, username, officerId; private final Role role;
    public User(String id,String username,String officerId,Role role){this.id=id;this.username=username;this.officerId=officerId;this.role=role;}
    public String getId(){return id;} public String getUsername(){return username;} public String getOfficerId(){return officerId;} public Role getRole(){return role;}
}
