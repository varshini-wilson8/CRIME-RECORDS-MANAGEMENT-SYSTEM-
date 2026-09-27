import com.sun.net.httpserver.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;

public final class LoginHandler implements HttpHandler {
    private final UserDao users;
    private final SessionManager sessions;
    private final AuditLogDao audit;

    public LoginHandler(UserDao users, SessionManager sessions, AuditLogDao audit) {
        this.users = users;
        this.sessions = sessions;
        this.audit = audit;
    }

    public LoginHandler(UserDao users, SessionManager sessions) {
        this(users, sessions, new AuditLogDao());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        exchange.getResponseHeaders().set("Pragma", "no-cache");

        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method)) {
            // If already logged in with a valid session, redirect to dashboard
            String token = HttpUtil.cookie(exchange, "CCECS_SESSION");
            if (token != null && sessions.get(token).isPresent()) {
                HttpUtil.redirect(exchange, "/dashboard");
                return;
            }

            Path loginHtml = Path.of("web", "login.html");
            if (Files.isRegularFile(loginHtml)) {
                HttpUtil.text(exchange, 200, Files.readString(loginHtml), "text/html");
            } else {
                HttpUtil.text(exchange, 200,
                        "<!doctype html><title>CRMS 2.0 Login</title><h1>CRMS 2.0</h1>" +
                        "<form method='post'><label>Username <input name='username' required></label>" +
                        "<label>Password <input type='password' name='password' required></label>" +
                        "<button>Sign in</button></form>", "text/html");
            }
            return;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        try {
            Map<String, String> form = HttpUtil.form(exchange);
            String username = form.getOrDefault("username", "").trim();
            String password = form.getOrDefault("password", "");

            Optional<User> optUser = users.authenticate(username, password.toCharArray());

            if (optUser.isEmpty()) {
                // Record failed login audit log
                try {
                    audit.record("SYSTEM", "LOGIN_FAILURE", "USER", username.isEmpty() ? "UNKNOWN" : username, "Failed authentication attempt");
                } catch (SQLException ex) {
                    System.err.println("Warning: could not record audit: " + ex.getMessage());
                }

                String accept = exchange.getRequestHeaders().getFirst("Accept");
                if (accept != null && accept.contains("application/json")) {
                    HttpUtil.text(exchange, 401, "{\"error\":\"Invalid username or password.\"}", "application/json");
                } else {
                    HttpUtil.text(exchange, 401, "Invalid username or password.", "text/plain");
                }
                return;
            }

            User u = optUser.get();
            UserSession s = sessions.create(u.getId(), u.getUsername(), u.getOfficerId(), u.getRole());

            // Record successful login audit log
            try {
                audit.record(u.getId(), "LOGIN_SUCCESS", "USER", u.getId(), "User authenticated successfully: " + u.getUsername() + " (" + u.getRole() + ")");
            } catch (SQLException ex) {
                System.err.println("Warning: could not record audit: " + ex.getMessage());
            }

            exchange.getResponseHeaders().set("Set-Cookie", "CCECS_SESSION=" + s.getToken() + "; HttpOnly; SameSite=Strict; Path=/");

            String accept = exchange.getRequestHeaders().getFirst("Accept");
            String xRequestedWith = exchange.getRequestHeaders().getFirst("X-Requested-With");
            if (accept != null && accept.contains("application/json") && "XMLHttpRequest".equalsIgnoreCase(xRequestedWith)) {
                HttpUtil.text(exchange, 200, "{\"status\":\"success\",\"redirect\":\"/dashboard\"}", "application/json");
            } else {
                HttpUtil.redirect(exchange, "/dashboard");
            }
        } catch (Exception ex) {
            HttpUtil.text(exchange, 500, "Login service unavailable.", "text/plain");
        }
    }
}
