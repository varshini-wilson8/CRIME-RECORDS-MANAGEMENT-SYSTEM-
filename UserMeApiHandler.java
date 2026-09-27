import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import model.Officer;

public final class UserMeApiHandler implements HttpHandler {
    private final SessionManager sessions;
    private final OfficerDao officers;

    public UserMeApiHandler(SessionManager sessions, OfficerDao officers) {
        this.sessions = sessions;
        this.officers = officers;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        exchange.getResponseHeaders().set("Pragma", "no-cache");

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String token = HttpUtil.cookie(exchange, "CCECS_SESSION");
        Optional<UserSession> optSession = token != null ? sessions.get(token) : Optional.empty();
        if (optSession.isEmpty()) {
            HttpUtil.text(exchange, 401, "{\"error\":\"Authentication required\"}", "application/json");
            return;
        }

        UserSession s = optSession.get();
        String displayName = s.getUsername();
        String badge = "";
        String contact = "";

        if (s.getOfficerId() != null && !s.getOfficerId().isBlank()) {
            try {
                Officer off = officers.findById(s.getOfficerId());
                if (off != null) {
                    displayName = off.getName();
                    badge = off.getBadgeNumber() != null ? off.getBadgeNumber() : "";
                    contact = off.getContact() != null ? off.getContact() : "";
                }
            } catch (SQLException ex) {
                // Keep default display name on DB error
            }
        }

        Role role = s.getRole();
        String roleTitle = role.getTitle();

        // Authoritative permissions list evaluated directly via Role.canAccess(module)
        String[][] modules = {
            {"DASHBOARD", "Operations Dashboard"},
            {"CASES", "Case Records"},
            {"EVIDENCE", "Evidence Custody & Registry"},
            {"INVESTIGATION", "Investigation Workspace"},
            {"MAP", "Geospatial Crime Map"},
            {"SUSPECTS", "Suspect Profiles"},
            {"OFFICERS", "Officer Workload & Management"},
            {"COMMAND_CENTER", "Command Center & Analytics"},
            {"CRIME_PREDICTION", "Crime Pattern Prediction"},
            {"AUDIT", "Case Audit Activity"},
            {"PACKAGE", "Case Dossier & Package Generation"}
        };

        StringBuilder sb = new StringBuilder();
        sb.append("{")
          .append("\"authenticated\":true,")
          .append("\"userId\":\"").append(escape(s.getUserId())).append("\",")
          .append("\"username\":\"").append(escape(s.getUsername())).append("\",")
          .append("\"displayName\":\"").append(escape(displayName)).append("\",")
          .append("\"role\":\"").append(role.name()).append("\",")
          .append("\"roleTitle\":\"").append(escape(roleTitle)).append("\",")
          .append("\"officerId\":").append(s.getOfficerId() != null ? "\"" + escape(s.getOfficerId()) + "\"" : "null").append(",")
          .append("\"badgeNumber\":").append(!badge.isEmpty() ? "\"" + escape(badge) + "\"" : "null").append(",")
          .append("\"contact\":").append(!contact.isEmpty() ? "\"" + escape(contact) + "\"" : "null").append(",")
          .append("\"status\":\"ACTIVE\",")
          .append("\"permissions\":[");

        for (int i = 0; i < modules.length; i++) {
            if (i > 0) sb.append(",");
            String code = modules[i][0];
            String name = modules[i][1];
            boolean allowed = role.canAccess(code);
            sb.append("{\"code\":\"").append(code).append("\",\"name\":\"").append(escape(name)).append("\",\"allowed\":").append(allowed).append("}");
        }

        sb.append("]}");
        HttpUtil.text(exchange, 200, sb.toString(), "application/json");
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
