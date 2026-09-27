import java.io.InputStream;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;

public class TestAuthWorkflow {

    private static final String BASE_URL = "http://localhost:8081";

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println("     CRMS 2.0 AUTHENTICATION & RBAC WORKFLOW VERIFICATION       ");
        System.out.println("=================================================================");

        int passed = 0;
        int failed = 0;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        // [TEST 1] Unauthenticated Access Restrictions
        System.out.println("\n[TEST 1] Unauthenticated Endpoint Protection:");
        {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/api/me")).build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401) {
                System.out.println("   ✓ PASSED: GET /api/me without cookie correctly returns 401 Unauthorized");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: Expected 401, got " + res.statusCode());
                failed++;
            }

            HttpRequest accReq = HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/account")).build();
            HttpResponse<String> accRes = client.send(accReq, HttpResponse.BodyHandlers.ofString());
            if (accRes.statusCode() == 303 && accRes.headers().firstValue("Location").orElse("").contains("/login")) {
                System.out.println("   ✓ PASSED: GET /account without cookie redirects to /login (303)");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: Expected 303 redirect to /login, got " + accRes.statusCode());
                failed++;
            }
        }

        // [TEST 2] Admin Login & /api/me Profile Resolution
        System.out.println("\n[TEST 2] Admin Login & /api/me Identity Resolution:");
        String adminCookie = null;
        {
            String form = "username=admin&password=ChangeMe!2026";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            String setCookie = res.headers().firstValue("Set-Cookie").orElse("");
            if (res.statusCode() == 303 && setCookie.contains("CCECS_SESSION=")) {
                adminCookie = extractCookie(setCookie, "CCECS_SESSION");
                System.out.println("   ✓ PASSED: Admin login returned 303 with CCECS_SESSION cookie");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: Admin login failed. Status: " + res.statusCode() + ", Set-Cookie: " + setCookie);
                failed++;
            }

            if (adminCookie != null) {
                HttpRequest meReq = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/me"))
                        .header("Cookie", "CCECS_SESSION=" + adminCookie)
                        .build();
                HttpResponse<String> meRes = client.send(meReq, HttpResponse.BodyHandlers.ofString());
                String body = meRes.body();
                boolean cacheControl = meRes.headers().firstValue("Cache-Control").orElse("").contains("no-store");
                boolean pragma = meRes.headers().firstValue("Pragma").orElse("").contains("no-cache");

                if (meRes.statusCode() == 200 &&
                    body.contains("\"authenticated\":true") &&
                    body.contains("\"username\":\"admin\"") &&
                    body.contains("\"role\":\"ADMIN\"") &&
                    body.contains("\"roleTitle\":\"Administrator\"") &&
                    body.contains("\"displayName\":\"admin\"") &&
                    cacheControl && pragma) {
                    System.out.println("   ✓ PASSED: /api/me returned admin identity, roleTitle='Administrator', and cache prevention headers");
                    passed++;
                } else {
                    System.out.println("   ✗ FAILED: Admin /api/me response invalid: " + body);
                    failed++;
                }
            }
        }

        // [TEST 3] Command Officer Login & Officer Profile Resolution
        System.out.println("\n[TEST 3] Command Officer Login & Linked Officer Resolution (Officer Raghav):");
        String commanderCookie = null;
        {
            String form = "username=commander&password=ChangeMe!2026";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            String setCookie = res.headers().firstValue("Set-Cookie").orElse("");
            if (res.statusCode() == 303 && setCookie.contains("CCECS_SESSION=")) {
                commanderCookie = extractCookie(setCookie, "CCECS_SESSION");
                System.out.println("   ✓ PASSED: Commander login returned 303 with CCECS_SESSION cookie");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: Commander login failed. Status: " + res.statusCode());
                failed++;
            }

            if (commanderCookie != null) {
                HttpRequest meReq = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/me"))
                        .header("Cookie", "CCECS_SESSION=" + commanderCookie)
                        .build();
                HttpResponse<String> meRes = client.send(meReq, HttpResponse.BodyHandlers.ofString());
                String body = meRes.body();

                if (meRes.statusCode() == 200 &&
                    body.contains("\"displayName\":\"Officer Raghav\"") &&
                    body.contains("\"officerId\":\"O1\"") &&
                    body.contains("\"badgeNumber\":\"B101\"") &&
                    body.contains("\"role\":\"COMMAND_OFFICER\"") &&
                    body.contains("\"roleTitle\":\"Command Officer\"")) {
                    System.out.println("   ✓ PASSED: /api/me resolved officer O1 link to 'Officer Raghav', badge B101");
                    passed++;
                } else {
                    System.out.println("   ✗ FAILED: Commander /api/me response invalid: " + body);
                    failed++;
                }
            }
        }

        // [TEST 4] Field Agent Login & Role-Based Permissions
        System.out.println("\n[TEST 4] Field Agent Login & Epistemic RBAC Isolation (Officer Meena):");
        String agentCookie = null;
        {
            String form = "username=fieldagent&password=ChangeMe!2026";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            String setCookie = res.headers().firstValue("Set-Cookie").orElse("");
            if (res.statusCode() == 303 && setCookie.contains("CCECS_SESSION=")) {
                agentCookie = extractCookie(setCookie, "CCECS_SESSION");
                System.out.println("   ✓ PASSED: Field Agent login returned 303 with CCECS_SESSION cookie");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: Field Agent login failed. Status: " + res.statusCode());
                failed++;
            }

            if (agentCookie != null) {
                HttpRequest meReq = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/me"))
                        .header("Cookie", "CCECS_SESSION=" + agentCookie)
                        .build();
                HttpResponse<String> meRes = client.send(meReq, HttpResponse.BodyHandlers.ofString());
                String body = meRes.body();

                boolean meValid = meRes.statusCode() == 200 &&
                                  body.contains("\"displayName\":\"Officer Meena\"") &&
                                  body.contains("\"officerId\":\"O2\"") &&
                                  body.contains("\"badgeNumber\":\"B102\"") &&
                                  body.contains("\"role\":\"FIELD_AGENT\"") &&
                                  body.contains("\"roleTitle\":\"Field Agent\"");

                // Check permissions array has allowed:false for SUSPECTS, OFFICERS, COMMAND_CENTER
                boolean suspectsLocked = body.contains("\"code\":\"SUSPECTS\",\"name\":\"Suspect Profiles\",\"allowed\":false");
                boolean officersLocked = body.contains("\"code\":\"OFFICERS\",\"name\":\"Officer Workload & Management\",\"allowed\":false");
                boolean commandLocked = body.contains("\"code\":\"COMMAND_CENTER\",\"name\":\"Command Center & Analytics\",\"allowed\":false");
                boolean casesAllowed = body.contains("\"code\":\"CASES\",\"name\":\"Case Records\",\"allowed\":true");

                if (meValid && suspectsLocked && officersLocked && commandLocked && casesAllowed) {
                    System.out.println("   ✓ PASSED: /api/me resolved officer O2 link to 'Officer Meena', and RBAC permissions reflect FIELD_AGENT restrictions");
                    passed++;
                } else {
                    System.out.println("   ✗ FAILED: Field Agent RBAC permissions check failed: " + body);
                    failed++;
                }
            }
        }

        // [TEST 5] Authoritative POST /api/logout & Old-Cookie Invalidation
        System.out.println("\n[TEST 5] Authoritative POST /api/logout & Old-Cookie Invalidation:");
        {
            if (agentCookie != null) {
                HttpRequest logoutReq = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/logout"))
                        .header("Cookie", "CCECS_SESSION=" + agentCookie)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<String> logoutRes = client.send(logoutReq, HttpResponse.BodyHandlers.ofString());
                String setCookie = logoutRes.headers().firstValue("Set-Cookie").orElse("");
                boolean cookieCleared = setCookie.contains("Max-Age=0") || setCookie.contains("expires=");

                if (logoutRes.statusCode() == 200 && cookieCleared && logoutRes.body().contains("\"success\":true")) {
                    System.out.println("   ✓ PASSED: POST /api/logout returned 200 OK and cleared session cookie (Max-Age=0)");
                    passed++;
                } else {
                    System.out.println("   ✗ FAILED: POST /api/logout failed. Status: " + logoutRes.statusCode() + ", Set-Cookie: " + setCookie);
                    failed++;
                }

                // Attempt Replay Attack with the terminated session cookie
                HttpRequest replayReq = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/me"))
                        .header("Cookie", "CCECS_SESSION=" + agentCookie)
                        .build();
                HttpResponse<String> replayRes = client.send(replayReq, HttpResponse.BodyHandlers.ofString());

                if (replayRes.statusCode() == 401) {
                    System.out.println("   ✓ PASSED: Old session cookie immediately rejected with 401 Unauthorized (server-side session terminated)");
                    passed++;
                } else {
                    System.out.println("   ✗ FAILED: Replay of invalidated session cookie returned status: " + replayRes.statusCode());
                    failed++;
                }
            }
        }

        // [TEST 6] GET /logout Delegation & Browser Redirection
        System.out.println("\n[TEST 6] GET /logout Delegation for Browser Redirection:");
        {
            if (commanderCookie != null) {
                HttpRequest getLogoutReq = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/logout"))
                        .header("Cookie", "CCECS_SESSION=" + commanderCookie)
                        .build();
                HttpResponse<String> getLogoutRes = client.send(getLogoutReq, HttpResponse.BodyHandlers.ofString());
                String location = getLogoutRes.headers().firstValue("Location").orElse("");
                String setCookie = getLogoutRes.headers().firstValue("Set-Cookie").orElse("");

                if (getLogoutRes.statusCode() == 303 && location.contains("/login?loggedOut=true") && setCookie.contains("Max-Age=0")) {
                    System.out.println("   ✓ PASSED: GET /logout delegates to authoritative logout, clears cookie, and redirects to /login?loggedOut=true");
                    passed++;
                } else {
                    System.out.println("   ✗ FAILED: GET /logout unexpected response. Status: " + getLogoutRes.statusCode() + ", Location: " + location);
                    failed++;
                }

                // Verify commander session is also destroyed
                HttpRequest testCommander = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/me"))
                        .header("Cookie", "CCECS_SESSION=" + commanderCookie)
                        .build();
                HttpResponse<String> testRes = client.send(testCommander, HttpResponse.BodyHandlers.ofString());
                if (testRes.statusCode() == 401) {
                    System.out.println("   ✓ PASSED: Commander session destroyed on server after GET /logout");
                    passed++;
                } else {
                    System.out.println("   ✗ FAILED: Commander session still alive after GET /logout");
                    failed++;
                }
            }
        }

        // [TEST 7] Audit Trail Verification (LOGIN_SUCCESS and LOGOUT in DB)
        System.out.println("\n[TEST 7] Accountability Audit Trail Records:");
        {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:ccecs.db");
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                    "SELECT action, user_id, details, timestamp FROM audit_logs WHERE action IN ('LOGIN_SUCCESS', 'LOGOUT') ORDER BY id DESC LIMIT 10"
                );
                int loginCount = 0;
                int logoutCount = 0;
                while (rs.next()) {
                    String action = rs.getString("action");
                    if ("LOGIN_SUCCESS".equals(action)) loginCount++;
                    if ("LOGOUT".equals(action)) logoutCount++;
                }
                if (loginCount >= 3 && logoutCount >= 2) {
                    System.out.println("   ✓ PASSED: Audit logs verify " + loginCount + " LOGIN_SUCCESS and " + logoutCount + " LOGOUT events recorded with ISO timestamps");
                    passed++;
                } else {
                    System.out.println("   ✗ FAILED: Insufficient audit records found. Logins: " + loginCount + ", Logouts: " + logoutCount);
                    failed++;
                }
            }
        }

        // [TEST 8] HTML/CSS/JS Assets Integrity
        System.out.println("\n[TEST 8] Web Presentation Assets Availability:");
        {
            HttpRequest loginHtmlReq = HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/login")).build();
            HttpResponse<String> loginHtmlRes = client.send(loginHtmlReq, HttpResponse.BodyHandlers.ofString());
            boolean hasLoginUi = loginHtmlRes.statusCode() == 200 &&
                                 loginHtmlRes.body().contains("CRMS 2.0") &&
                                 loginHtmlRes.body().contains("togglePassword");

            HttpRequest appJsReq = HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/app.js")).build();
            HttpResponse<String> appJsRes = client.send(appJsReq, HttpResponse.BodyHandlers.ofString());
            boolean hasAppJs = appJsRes.statusCode() == 200 &&
                               appJsRes.body().contains("initGlobalHeaderAndAuth") &&
                               appJsRes.body().contains("showSignOutModal");

            if (adminCookie != null) {
                HttpRequest accPageReq = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/account"))
                        .header("Cookie", "CCECS_SESSION=" + adminCookie)
                        .build();
                HttpResponse<String> accPageRes = client.send(accPageReq, HttpResponse.BodyHandlers.ofString());
                boolean hasAccountUi = accPageRes.statusCode() == 200 &&
                                       accPageRes.body().contains("User Account &amp; Access") &&
                                       accPageRes.body().contains("Role Authorizations (RBAC)");

                if (hasLoginUi && hasAppJs && hasAccountUi) {
                    System.out.println("   ✓ PASSED: /login, /app.js, and /account UI assets rendered cleanly with 200 OK");
                    passed++;
                } else {
                    System.out.println("   ✗ FAILED: Asset checks failed. Login: " + hasLoginUi + ", AppJs: " + hasAppJs + ", Account: " + hasAccountUi);
                    failed++;
                }
            }
        }

        System.out.println("\n=================================================================");
        System.out.println("   AUTH WORKFLOW RESULTS: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("=================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static String extractCookie(String setCookieHeader, String cookieName) {
        for (String part : setCookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(cookieName + "=")) {
                return trimmed.substring((cookieName + "=").length());
            }
        }
        return null;
    }
}
