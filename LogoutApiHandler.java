import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

public final class LogoutApiHandler implements HttpHandler {
    private final SessionManager sessions;
    private final AuditLogDao audit;

    public LogoutApiHandler(SessionManager sessions, AuditLogDao audit) {
        this.sessions = sessions;
        this.audit = audit;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"POST".equalsIgnoreCase(method) && !"GET".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        // 1. Identify active session and destroy it server-side
        String token = HttpUtil.cookie(exchange, "CCECS_SESSION");
        if (token != null) {
            Optional<UserSession> session = sessions.get(token);
            if (session.isPresent()) {
                String userId = session.get().getUserId();
                try {
                    audit.record(userId, "LOGOUT", "USER", userId, "User signed out successfully");
                } catch (SQLException ex) {
                    System.err.println("Warning: could not record logout audit: " + ex.getMessage());
                }
            }
            sessions.destroy(token);
        }

        // 2. Clear cookie and apply cache-prevention headers
        exchange.getResponseHeaders().set("Set-Cookie", "CCECS_SESSION=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        exchange.getResponseHeaders().set("Pragma", "no-cache");

        // 3. Response: JSON for API/POST, or redirect for browser GET
        boolean isApiOrPost = "POST".equalsIgnoreCase(method) || exchange.getRequestURI().getPath().startsWith("/api/");
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        if (isApiOrPost || (accept != null && accept.contains("application/json"))) {
            HttpUtil.text(exchange, 200, "{\"success\":true,\"status\":\"logged_out\",\"message\":\"Signed out successfully\"}", "application/json");
        } else {
            HttpUtil.redirect(exchange, "/login?loggedOut=true");
        }
    }
}
