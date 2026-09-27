import com.sun.net.httpserver.*;
import java.io.IOException;
import java.util.*;

/** Reusable RBAC guard with cache-prevention headers. Browser page requests redirect; API requests receive 401/403. */
public final class RoleProtectedHandler implements HttpHandler {
    private final SessionManager sessions;
    private final Set<Role> permitted;
    private final HttpHandler delegate;

    public RoleProtectedHandler(SessionManager sessions, Set<Role> permitted, HttpHandler delegate) {
        this.sessions = sessions;
        this.permitted = permitted;
        this.delegate = delegate;
    }

    @Override
    public void handle(HttpExchange e) throws IOException {
        e.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        e.getResponseHeaders().set("Pragma", "no-cache");

        String token = HttpUtil.cookie(e, "CCECS_SESSION");
        Optional<UserSession> session = token == null ? Optional.empty() : sessions.get(token);
        boolean api = e.getRequestURI().getPath().startsWith("/api/");

        if (session.isEmpty()) {
            if (api) {
                HttpUtil.text(e, 401, "{\"error\":\"Authentication required\"}", "application/json");
            } else {
                HttpUtil.redirect(e, "/login");
            }
            return;
        }

        if (!permitted.contains(session.get().getRole())) {
            if (api) {
                HttpUtil.text(e, 403, "{\"error\":\"Forbidden\"}", "application/json");
            } else {
                HttpUtil.text(e, 403, "<h1>403 Forbidden</h1>", "text/html");
            }
            return;
        }

        delegate.handle(e);
    }
}
