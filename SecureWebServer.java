import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import model.Officer;

public final class SecureWebServer {
    static final SessionManager S = new SessionManager();
    static final UserDao U = new UserDao();
    static final CaseDao C = new CaseDao();
    static final OfficerDao O = new OfficerDao();
    static final CaseEventDao E = new CaseEventDao();
    static final PersonDao P = new PersonDao();
    static final InvestigationTaskDao T = new InvestigationTaskDao();
    static final InvestigationFindingDao F = new InvestigationFindingDao();
    static final GeospatialDao G = new GeospatialDao();
    static final AuditLogDao A = new AuditLogDao();
    static final HypothesisDao H = new HypothesisDao();
    static final CrimePredictionDao CP_DAO = new CrimePredictionDao();
    static final CrimePatternPredictionEngine CP_ENGINE = new CrimePatternPredictionEngine();

    public static void main(String[] a) throws Exception {
        Database.initialize();
        seedOfficers();
        U.createDefaultUsersIfMissing();
        SampleDataSeeder.seed();

        // Train and initialize AI Crime Pattern Prediction Engine
        try {
            var trainingDataset = CP_DAO.extractHistoricalDataset();
            CP_ENGINE.train(trainingDataset);
            System.out.println("AI Crime Pattern Engine initialized with " + trainingDataset.size() + " observations.");
        } catch (Exception e) {
            System.err.println("Warning: AI Crime Pattern Engine initialization failed: " + e.getMessage());
        }

        HttpServer h = HttpServer.create(new InetSocketAddress("localhost", 8081), 0);

        h.createContext("/login", new LoginHandler(U, S, A));
        h.createContext("/api/logout", new LogoutApiHandler(S, A));
        h.createContext("/logout", new LogoutApiHandler(S, A));

        // Dedicated UI Pages with Role-Based Access Control
        h.createContext("/", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new Page("index.html")));
        h.createContext("/dashboard", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new Page("index.html")));
        h.createContext("/cases", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new Page("cases.html")));
        h.createContext("/evidence", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new Page("evidence.html")));
        h.createContext("/suspects", new RoleProtectedHandler(S, EnumSet.of(Role.ADMIN, Role.COMMAND_OFFICER), new Page("suspects.html")));
        h.createContext("/officers", new RoleProtectedHandler(S, EnumSet.of(Role.ADMIN, Role.COMMAND_OFFICER), new Page("officers.html")));
        h.createContext("/investigation", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new Page("investigation.html")));
        h.createContext("/command-center", new RoleProtectedHandler(S, EnumSet.of(Role.ADMIN, Role.COMMAND_OFFICER), new Page("command-center.html")));
        h.createContext("/map", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new Page("map.html")));
        h.createContext("/crime-prediction", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new Page("crime-prediction.html")));
        h.createContext("/account", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new Page("account.html")));

        // Static Assets
        h.createContext("/styles.css", SecureWebServer::asset);
        h.createContext("/app.js", SecureWebServer::asset);

        // Core APIs
        h.createContext("/api/cases", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new CaseApiHandler(C, O, S)));
        h.createContext("/api/case-priority", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new CasePriorityApiHandler()));
        h.createContext("/api/case-events", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new CaseEventApiHandler(E, C, S)));
        h.createContext("/api/case-people", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new PersonApiHandler(P, C)));
        h.createContext("/api/case-graph", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new CaseGraphApiHandler(C)));
        h.createContext("/api/investigation-tasks", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new InvestigationTaskApiHandler(T, C, S)));
        h.createContext("/api/investigation-findings", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new InvestigationFindingApiHandler(F, C, S)));
        h.createContext("/api/investigation-hypotheses", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new HypothesisApiHandler(H, C, S)));
        h.createContext("/api/case-reports", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new CaseReportApiHandler(C, S)));
        h.createContext("/api/evidence", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new SecureDataHandler("evidence", S)));
        h.createContext("/api/case-audit", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new AuditLogApiHandler(C)));
        h.createContext("/api/case-intelligence", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new CaseIntelligenceApiHandler(C)));
        h.createContext("/api/command-center", new RoleProtectedHandler(S, EnumSet.of(Role.ADMIN, Role.COMMAND_OFFICER), new CommandCenterApiHandler()));
        h.createContext("/api/dashboard-operations", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new DashboardOperationsApiHandler()));
        h.createContext("/api/geospatial", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new GeospatialApiHandler(G, A, S)));
        h.createContext("/api/suspects", new RoleProtectedHandler(S, EnumSet.of(Role.ADMIN, Role.COMMAND_OFFICER), new SecureDataHandler("suspects")));
        h.createContext("/api/officers", new RoleProtectedHandler(S, EnumSet.of(Role.ADMIN, Role.COMMAND_OFFICER), new SecureDataHandler("officers")));
        h.createContext("/api/me", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new UserMeApiHandler(S, O)));
        h.createContext("/account/password", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), new ChangePasswordHandler(U, S)));

        // AI Crime Pattern Prediction APIs
        CrimePredictionApiHandler cpHandler = new CrimePredictionApiHandler(CP_ENGINE, CP_DAO, A, S);
        h.createContext("/api/ai/patterns", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), cpHandler));
        h.createContext("/api/ai/predict", new RoleProtectedHandler(S, EnumSet.allOf(Role.class), cpHandler));

        h.start();
        System.out.println("Secure CCECS is running at http://localhost:8081/login");
    }

    static void seedOfficers() throws SQLException {
        if (!O.findAll().isEmpty()) return;
        O.save(new Officer("O1", "Officer Raghav", "9000000001", "B101"));
        O.save(new Officer("O2", "Officer Meena", "9000000002", "B102"));
        O.save(new Officer("O3", "Officer Arjun", "9000000003", "B103"));
    }

    static void asset(HttpExchange e) throws IOException {
        Path p = Path.of("web", e.getRequestURI().getPath().substring(1));
        if (!Files.isRegularFile(p)) {
            e.sendResponseHeaders(404, -1);
            return;
        }
        byte[] b = Files.readAllBytes(p);
        e.getResponseHeaders().set("Content-Type", p.toString().endsWith("css") ? "text/css" : "application/javascript");
        e.sendResponseHeaders(200, b.length);
        e.getResponseBody().write(b);
        e.close();
    }

    static final class Page implements HttpHandler {
        final String file;
        Page(String file) { this.file = file; }
        public void handle(HttpExchange e) throws IOException {
            Path p = Path.of("web", file);
            if (!Files.isRegularFile(p)) {
                e.sendResponseHeaders(404, -1);
                return;
            }
            HttpUtil.text(e, 200, Files.readString(p), "text/html");
        }
    }
}
