import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {
    private final ConcurrentHashMap<String,UserSession> sessions=new ConcurrentHashMap<>(); private final SecureRandom random=new SecureRandom();
    public UserSession create(String userId,String username,String officerId,Role role){byte[] b=new byte[32];random.nextBytes(b);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(b);UserSession s=new UserSession(token,userId,username,officerId,role,Instant.now().plus(Duration.ofHours(8)));sessions.put(token,s);return s;}
    public Optional<UserSession> get(String token){UserSession s=sessions.get(token);if(s==null||s.expired()){sessions.remove(token);return Optional.empty();}return Optional.of(s);} public void destroy(String token){sessions.remove(token);}
}
