import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import model.CaseRecord;
import model.CaseStatus;
import model.Evidence;
import model.EvidenceLog;
import model.Officer;
import model.Severity;
import model.Suspect;
import repository.CaseRepository;
import service.CaseService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small dependency-free web server for the CCECS dashboard.
 * Start with: java -cp out WebServer, then visit http://localhost:8080
 */
public class WebServer {
    private static final int PORT = 8080;
    private static final Path WEB_ROOT = Path.of("web");
    private static final SessionManager SESSIONS = new SessionManager();
    private static final UserDao USERS = new UserDao();
    private static final Set<Role> ALL_ROLES = EnumSet.allOf(Role.class);
    private static final Set<Role> ADMIN_OR_COMMAND = EnumSet.of(Role.ADMIN, Role.COMMAND_OFFICER);
    private static final CaseService caseService = createService();

    public static void main(String[] args) throws IOException, SQLException {
        Database.initialize();
        USERS.createDefaultUsersIfMissing();

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);
        server.createContext("/login", new LoginHandler(USERS, SESSIONS));
        server.createContext("/logout", exchange -> {
            String token = HttpUtil.cookie(exchange, "CCECS_SESSION");
            if (token != null) SESSIONS.destroy(token);
            exchange.getResponseHeaders().add("Set-Cookie", "CCECS_SESSION=; Max-Age=0; Path=/");
            HttpUtil.redirect(exchange, "/login");
        });
        server.createContext("/account/password", new RoleProtectedHandler(SESSIONS, ALL_ROLES, new ChangePasswordHandler(USERS, SESSIONS)));
        server.createContext("/", new RoleProtectedHandler(SESSIONS, ALL_ROLES, WebServer::serveStatic));
        server.createContext("/dashboard", new RoleProtectedHandler(SESSIONS, ALL_ROLES, WebServer::serveStatic));
        server.createContext("/api/cases", new RoleProtectedHandler(SESSIONS, ALL_ROLES, WebServer::handleCases));
        server.createContext("/api/officers", new RoleProtectedHandler(SESSIONS, ADMIN_OR_COMMAND, WebServer::handleOfficers));
        server.createContext("/api/suspects", new RoleProtectedHandler(SESSIONS, ADMIN_OR_COMMAND, WebServer::handleSuspects));
        server.createContext("/api/evidence", new RoleProtectedHandler(SESSIONS, ALL_ROLES, WebServer::handleEvidence));
        server.createContext("/styles.css", WebServer::serveStatic);
        server.createContext("/app.js", WebServer::serveStatic);
        server.setExecutor(null);
        server.start();
        System.out.println("CCECS dashboard is running at http://localhost:" + PORT + "/login");
        System.out.println("Default accounts: admin / ChangeMe!2026 | commander / ChangeMe!2026 | fieldagent / ChangeMe!2026");
        System.out.println("Press Ctrl+C to stop the web server.");
    }

    private static CaseService createService() {
        CaseService service = new CaseService(new CaseRepository());
        service.addOfficer(new Officer("O1", "Officer Raghav", "9000000001", "B101"));
        service.addOfficer(new Officer("O2", "Officer Meena", "9000000002", "B102"));
        service.addOfficer(new Officer("O3", "Officer Arjun", "9000000003", "B103"));
        seedSampleData(service);
        return service;
    }

    /** Fictional records included only to make the dashboard easy to explore. */
    private static void seedSampleData(CaseService service) {
        CaseRecord gallery = addSampleCase(service, "C-2026-041", "Riverside Gallery Break-in",
                "A fictional overnight break-in at a local gallery; inventory review is underway.",
                Severity.CRITICAL, CaseStatus.UNDER_INVESTIGATION, 6);
        CaseRecord missing = addSampleCase(service, "C-2026-042", "Missing Person Welfare Check",
                "A fictional welfare-check case with the last confirmed sighting near the transit hub.",
                Severity.HIGH, CaseStatus.OPEN, 3);
        CaseRecord fraud = addSampleCase(service, "C-2026-043", "Vendor Invoice Review",
                "A fictional review of irregular invoices submitted to a small business.",
                Severity.MEDIUM, CaseStatus.COLD, 18);
        CaseRecord recovery = addSampleCase(service, "C-2026-044", "Recovered Vehicle Report",
                "A fictional vehicle-recovery report completed after the owner verified possession.",
                Severity.LOW, CaseStatus.CLOSED, 11);

        Suspect sampleSuspect = new Suspect("S-1001", "Jordan Taylor", "555-0148",
                "Medium build, dark jacket", "MEDIUM");
        service.linkSuspectToCase(gallery, sampleSuspect);
        service.linkSuspectToCase(recovery, sampleSuspect); // intentionally triggers repeat-offender logic

        Evidence cameraFootage = new Evidence("E-041-A", "Gallery entrance camera footage", LocalDate.now().minusDays(6), "Officer Raghav");
        cameraFootage.transferCustody("Digital Forensics Unit", "Submitted for authenticity review");
        gallery.addEvidence(cameraFootage);
        missing.addEvidence(new Evidence("E-042-A", "Witness statement form", LocalDate.now().minusDays(3), "Officer Meena"));
        fraud.addEvidence(new Evidence("E-043-A", "Invoice document bundle", LocalDate.now().minusDays(18), "Officer Arjun"));
    }

    private static CaseRecord addSampleCase(CaseService service, String id, String title, String description,
                                             Severity severity, CaseStatus status, int daysOpen) {
        CaseRecord record = new CaseRecord(id, title, description, severity);
        record.setDateOpened(LocalDate.now().minusDays(daysOpen));
        record.setStatus(status);
        service.addCaseWithAutoAssignment(record);
        return record;
    }

    private static void handleCases(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("GET")) {
            sendJson(exchange, 200, casesJson(caseService.getCasesSortedByPriority()));
            return;
        }

        UserSession session = SESSIONS.get(HttpUtil.cookie(exchange, "CCECS_SESSION")).orElse(null);
        if (session != null && session.getRole() == Role.FIELD_AGENT) {
            sendJson(exchange, 403, "{\"error\":\"Field agents cannot modify cases\"}");
            return;
        }

        if (exchange.getRequestMethod().equals("PUT")) {
            updateCase(exchange);
            return;
        }
        if (!exchange.getRequestMethod().equals("POST")) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
            return;
        }

        Map<String, String> form = formData(exchange);
        String id = required(form, "caseId");
        String title = required(form, "title");
        String description = required(form, "description");
        String severityText = required(form, "severity");
        if (id == null || title == null || description == null || severityText == null) {
            sendJson(exchange, 400, errorJson("Case ID, title, description, and severity are required."));
            return;
        }
        if (caseService.searchById(id) != null) {
            sendJson(exchange, 409, errorJson("A case with this ID already exists."));
            return;
        }
        try {
            CaseRecord record = new CaseRecord(id, title, description, Severity.valueOf(severityText));
            Officer assigned = caseService.addCaseWithAutoAssignment(record);
            sendJson(exchange, 201, "{\"message\":\"Case created\",\"assignedOfficer\":\"" + json(assigned.getName()) + "\"}");
        } catch (IllegalArgumentException ex) {
            sendJson(exchange, 400, errorJson("Choose a valid severity."));
        }
    }

    private static void updateCase(HttpExchange exchange) throws IOException {
        Map<String, String> form = formData(exchange);
        CaseRecord record = caseService.searchById(form.get("caseId"));
        if (record == null) { sendJson(exchange, 404, errorJson("Case not found.")); return; }
        try {
            record.setTitle(required(form, "title"));
            record.setDescription(required(form, "description"));
            record.setSeverity(Severity.valueOf(required(form, "severity")));
            record.setStatus(CaseStatus.valueOf(required(form, "status")));
            record.setDateOpened(LocalDate.parse(required(form, "opened")));
            String officerId = required(form, "officerId");
            Officer replacement = officerById(officerId);
            if (replacement == null) throw new IllegalArgumentException();
            if (record.getAssignedOfficer() != replacement) {
                if (record.getAssignedOfficer() != null) record.getAssignedOfficer().decrementActiveCaseCount();
                record.assignOfficer(replacement);
            }
            sendJson(exchange, 200, "{\"message\":\"Case updated\"}");
        } catch (Exception ex) { sendJson(exchange, 400, errorJson("Please complete every case field with valid values.")); }
    }

    private static void handleOfficers(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("GET")) { sendJson(exchange, 200, officersJson()); return; }
        if (!exchange.getRequestMethod().equals("POST")) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
            return;
        }
        Map<String, String> form = formData(exchange);
        String id = required(form, "id"), name = required(form, "name"), contact = required(form, "contact"), badge = required(form, "badge");
        if (id == null || name == null || contact == null || badge == null || officerById(id) != null) {
            sendJson(exchange, 400, errorJson("Provide unique officer ID, name, contact, and badge number.")); return;
        }
        caseService.addOfficer(new Officer(id, name, contact, badge));
        sendJson(exchange, 201, "{\"message\":\"Officer profile created\"}");
    }

    private static void handleSuspects(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("GET")) { sendJson(exchange, 200, suspectsJson()); return; }
        if (!exchange.getRequestMethod().equals("POST")) { sendJson(exchange, 405, errorJson("Method not allowed")); return; }
        Map<String, String> form = formData(exchange);
        CaseRecord record = caseService.searchById(form.get("caseId"));
        if (record == null) { sendJson(exchange, 404, errorJson("Case not found.")); return; }
        String id = required(form, "id");
        Suspect suspect = caseService.getRepository().findSuspectById(id);
        if (suspect == null) {
            String name = required(form, "name"), contact = required(form, "contact"), description = required(form, "physicalDescription"), risk = required(form, "riskLevel");
            if (id == null || name == null || contact == null || description == null || risk == null) { sendJson(exchange, 400, errorJson("Complete all suspect fields.")); return; }
            suspect = new Suspect(id, name, contact, description, risk.toUpperCase());
        }
        boolean flagged = caseService.linkSuspectToCase(record, suspect);
        sendJson(exchange, 201, "{\"message\":\"Suspect linked\",\"repeatOffender\":" + (flagged || suspect.isRepeatOffender()) + "}");
    }

    private static void handleEvidence(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("GET")) { sendJson(exchange, 200, evidenceJson()); return; }
        if (!exchange.getRequestMethod().equals("POST")) { sendJson(exchange, 405, errorJson("Method not allowed")); return; }
        Map<String, String> form = formData(exchange);
        Evidence evidence = findEvidence(form.get("evidenceId"));
        if (evidence != null && form.containsKey("transferTo")) {
            String transferTo = required(form, "transferTo"), remarks = required(form, "remarks");
            if (transferTo == null || remarks == null) { sendJson(exchange, 400, errorJson("Transfer recipient and remarks are required.")); return; }
            evidence.transferCustody(transferTo, remarks); sendJson(exchange, 200, "{\"message\":\"Custody transferred\"}"); return;
        }
        CaseRecord record = caseService.searchById(form.get("caseId"));
        String id = required(form, "evidenceId"), description = required(form, "description"), custodian = required(form, "custodian");
        if (record == null || id == null || description == null || custodian == null || evidence != null) { sendJson(exchange, 400, errorJson("Use a valid case and a unique evidence ID; complete every field.")); return; }
        record.addEvidence(new Evidence(id, description, LocalDate.now(), custodian));
        sendJson(exchange, 201, "{\"message\":\"Evidence added\"}");
    }

    private static void serveStatic(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String requested = exchange.getRequestURI().getPath();
        Path file;
        if (requested.equals("/") || requested.equals("/dashboard") || requested.equals("/index.html")) {
            file = WEB_ROOT.resolve("index.html");
        } else {
            file = WEB_ROOT.resolve(requested.substring(1)).normalize();
        }
        if (!file.startsWith(WEB_ROOT) || !Files.isRegularFile(file)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType(file));
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String casesJson(List<CaseRecord> cases) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < cases.size(); i++) {
            CaseRecord c = cases.get(i);
            if (i > 0) result.append(',');
            result.append("{\"caseId\":\"").append(json(c.getCaseId()))
                    .append("\",\"title\":\"").append(json(c.getTitle()))
                    .append("\",\"description\":\"").append(json(c.getDescription()))
                    .append("\",\"severity\":\"").append(c.getSeverity())
                    .append("\",\"status\":\"").append(c.getStatus())
                    .append("\",\"opened\":\"").append(c.getDateOpened().format(DateTimeFormatter.ISO_DATE))
                    .append("\",\"priority\":").append(c.calculatePriorityScore())
                    .append(",\"officer\":\"").append(json(c.getAssignedOfficer() == null ? "Unassigned" : c.getAssignedOfficer().getName()))
                    .append("\",\"suspects\":").append(c.getLinkedSuspects().size())
                    .append(",\"evidence\":").append(c.getEvidenceList().size()).append('}');
        }
        return result.append(']').toString();
    }

    private static String officersJson() {
        StringBuilder result = new StringBuilder("[");
        List<Officer> officers = caseService.getAllOfficers();
        for (int i = 0; i < officers.size(); i++) {
            Officer o = officers.get(i); if (i > 0) result.append(',');
            result.append("{\"id\":\"").append(json(o.getId())).append("\",\"name\":\"").append(json(o.getName()))
                    .append("\",\"contact\":\"").append(json(o.getContact())).append("\",\"badge\":\"").append(json(o.getBadgeNumber()))
                    .append("\",\"activeCases\":").append(o.getActiveCaseCount()).append('}');
        }
        return result.append(']').toString();
    }

    private static String suspectsJson() {
        StringBuilder result = new StringBuilder("["); List<Suspect> suspects = caseService.getRepository().getAllSuspects();
        for (int i = 0; i < suspects.size(); i++) {
            Suspect s = suspects.get(i); if (i > 0) result.append(',');
            result.append("{\"id\":\"").append(json(s.getId())).append("\",\"name\":\"").append(json(s.getName()))
                    .append("\",\"risk\":\"").append(json(s.getRiskLevel())).append("\",\"repeatOffender\":").append(s.isRepeatOffender()).append('}');
        }
        return result.append(']').toString();
    }

    private static String evidenceJson() {
        StringBuilder result = new StringBuilder("["); boolean first = true;
        for (CaseRecord c : caseService.getAllCases()) for (Evidence e : c.getEvidenceList()) {
            if (!first) result.append(','); first = false;
            result.append("{\"caseId\":\"").append(json(c.getCaseId())).append("\",\"evidenceId\":\"").append(json(e.getEvidenceId()))
                    .append("\",\"description\":\"").append(json(e.getDescription())).append("\",\"custodian\":\"").append(json(e.getCurrentCustodian())).append("\",\"logs\":[");
            List<EvidenceLog> logs = e.getCustodyHistory();
            for (int i = 0; i < logs.size(); i++) { EvidenceLog log = logs.get(i); if (i > 0) result.append(',');
                result.append("{\"time\":\"").append(json(log.getTimestamp().toString().replace('T', ' '))).append("\",\"from\":\"").append(json(log.getTransferredFrom()))
                        .append("\",\"to\":\"").append(json(log.getTransferredTo())).append("\",\"remarks\":\"").append(json(log.getRemarks())).append("\"}"); }
            result.append("]}");
        }
        return result.append(']').toString();
    }

    private static Officer officerById(String id) {
        if (id == null) return null;
        for (Officer officer : caseService.getAllOfficers()) if (officer.getId().equalsIgnoreCase(id)) return officer;
        return null;
    }

    private static Evidence findEvidence(String id) {
        if (id == null) return null;
        for (CaseRecord record : caseService.getAllCases()) for (Evidence evidence : record.getEvidenceList())
            if (evidence.getEvidenceId().equalsIgnoreCase(id)) return evidence;
        return null;
    }

    private static Map<String, String> formData(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(decode(parts[0]), parts.length == 2 ? decode(parts[1]) : "");
        }
        return values;
    }

    private static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
    private static String required(Map<String, String> form, String field) {
        String value = form.get(field);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
    private static String errorJson(String message) { return "{\"error\":\"" + json(message) + "\"}"; }
    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
    private static String contentType(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        return "text/html; charset=utf-8";
    }
}
