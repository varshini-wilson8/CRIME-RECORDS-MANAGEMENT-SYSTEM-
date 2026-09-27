import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Stores a random salt and PBKDF2-HMAC-SHA256 derived key together. */
public final class PasswordHasher {
    private static final SecureRandom RANDOM = new SecureRandom(); private static final int ITERATIONS=210_000, KEY_BITS=256;
    private PasswordHasher() {}
    public static String hash(char[] password) { byte[] salt=new byte[16]; RANDOM.nextBytes(salt); return Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(password,salt)); }
    public static boolean verify(char[] password,String stored) { String[] parts=stored.split(":",2); if(parts.length!=2)return false; byte[] expected=Base64.getDecoder().decode(parts[1]), actual=derive(password,Base64.getDecoder().decode(parts[0])); int diff=expected.length^actual.length; for(int i=0;i<Math.min(expected.length,actual.length);i++)diff|=expected[i]^actual[i]; return diff==0; }
    private static byte[] derive(char[] password,byte[] salt) { try { KeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,KEY_BITS); return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); } catch(Exception e){throw new IllegalStateException("Password hashing unavailable",e);} }
}
