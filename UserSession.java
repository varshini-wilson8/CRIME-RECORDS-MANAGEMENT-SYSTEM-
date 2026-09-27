import java.time.Instant;

/** Java 11-compatible immutable authenticated session. */
public final class UserSession {
    private final String token, userId, username, officerId; private final Role role; private final Instant expiresAt;
    public UserSession(String token,String userId,String username,String officerId,Role role,Instant expiresAt){this.token=token;this.userId=userId;this.username=username;this.officerId=officerId;this.role=role;this.expiresAt=expiresAt;}
    public String getToken(){return token;} public String getUserId(){return userId;} public String getUsername(){return username;} public String getOfficerId(){return officerId;} public Role getRole(){return role;} public Instant getExpiresAt(){return expiresAt;}
    public boolean expired(){return Instant.now().isAfter(expiresAt);}
}
