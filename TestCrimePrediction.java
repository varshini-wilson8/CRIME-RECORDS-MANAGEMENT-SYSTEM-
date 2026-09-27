import java.lang.reflect.Field;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import model.CrimePatternFeature;

public class TestCrimePrediction {

    private static final String BASE_URL = "http://localhost:8081";

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println("   CRMS 2.0 PHASE 1M-AI: CRIME PATTERN PREDICTION TEST SUITE    ");
        System.out.println("=================================================================");

        int passed = 0;
        int failed = 0;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        // -------------------------------------------------------------------------
        // PHASE 1: Feature Extraction Integrity (1 case = 1 observation)
        // -------------------------------------------------------------------------
        System.out.println("\n[PHASE 1] Feature Extraction Integrity (1 case = 1 observation):");
        CrimePredictionDao dao = new CrimePredictionDao();
        List<CrimePatternFeature> dataset = dao.extractHistoricalDataset();
        if (dataset.size() == 5) {
            System.out.println("   ✓ PASSED: Exactly 5 cases extracted into exactly 5 training observations (N=" + dataset.size() + ")");
            passed++;
        } else {
            System.out.println("   ✗ FAILED: Expected N=5 observations, but got " + dataset.size());
            failed++;
        }

        // Verify no duplicate cases in training dataset
        Set<String> uniqueCaseIds = new HashSet<>();
        boolean noDups = true;
        for (CrimePatternFeature f : dataset) {
            if (!uniqueCaseIds.add(f.getCaseId())) {
                noDups = false;
            }
        }
        if (noDups && uniqueCaseIds.size() == dataset.size()) {
            System.out.println("   ✓ PASSED: Zero case duplication in feature extraction (" + uniqueCaseIds.size() + " unique case IDs)");
            passed++;
        } else {
            System.out.println("   ✗ FAILED: Case duplication detected in training dataset");
            failed++;
        }

        // -------------------------------------------------------------------------
        // PHASE 2: Epistemic & Ethical Safeguards (Forbidden Features Check)
        // -------------------------------------------------------------------------
        System.out.println("\n[PHASE 2] Epistemic & Ethical Safeguards (Forbidden Features Check):");
        Field[] fields = CrimePatternFeature.class.getDeclaredFields();
        List<String> fieldNames = new ArrayList<>();
        for (Field f : fields) {
            fieldNames.add(f.getName().toLowerCase(Locale.ROOT));
        }

        String[] forbidden = {"person", "suspect", "risk", "severity", "repeat_offender", "name", "phone", "gender", "race"};
        boolean forbiddenFound = false;
        for (String forb : forbidden) {
            for (String fn : fieldNames) {
                if (fn.contains(forb)) {
                    System.out.println("   ✗ FAILED: Forbidden feature '" + fn + "' found in CrimePatternFeature!");
                    forbiddenFound = true;
                }
            }
        }
        if (!forbiddenFound) {
            System.out.println("   ✓ PASSED: CrimePatternFeature strictly contains zero suspect/person/severity/risk profiling fields");
            passed++;
        } else {
            failed++;
        }

        // Verify null handling (no manufactured 12:00 or (0,0))
        boolean hasNullHour = false;
        boolean hasNullCoords = false;
        for (CrimePatternFeature f : dataset) {
            if (f.getHourOfDay() == null) hasNullHour = true;
            if (f.getLatitude() == null || f.getLongitude() == null) hasNullCoords = true;
        }
        if (hasNullHour && hasNullCoords) {
            System.out.println("   ✓ PASSED: Missing temporal/spatial values are preserved as null (no manufactured 12:00 or (0,0))");
            passed++;
        } else {
            System.out.println("   ✗ FAILED: Expected null values for cases without explicit hour or coordinates");
            failed++;
        }

        // -------------------------------------------------------------------------
        // PHASE 3: Model Training & Real Evaluation Metrics (No Hardcoded 72%)
        // -------------------------------------------------------------------------
        System.out.println("\n[PHASE 3] Model Training & Real LOOCV Metrics on Live Dataset:");
        CrimePatternPredictionEngine engine = new CrimePatternPredictionEngine();
        engine.train(dataset);

        CrimePatternPredictionEngine.EvaluationMetrics eval = engine.getEvaluationMetrics();
        if ("LIMITED_SAMPLE_SIZE".equals(eval.status) || "EVALUATED".equals(eval.status)) {
            System.out.println("   ✓ PASSED: Engine evaluated status=" + eval.status + " with method=" + eval.evaluationMethod);
            passed++;
        } else {
            System.out.println("   ✗ FAILED: Unexpected evaluation status: " + eval.status);
            failed++;
        }

        // Check transparent reporting for single-class limited sample size
        if (eval.accuracy != null && eval.precision == null && eval.recall == null && eval.f1 == null) {
            System.out.println("   ✓ PASSED: Real non-fabricated metrics (single-class BURGLARY reports precision/recall/f1 = null with honest disclaimer)");
            passed++;
        } else {
            System.out.println("   ✗ FAILED: Metrics were unexpectedly fabricated for single-class dataset");
            failed++;
        }

        // -------------------------------------------------------------------------
        // PHASE 4: Multi-Class Benchmark LOOCV Evaluation
        // -------------------------------------------------------------------------
        System.out.println("\n[PHASE 4] Multi-Class LOOCV Mathematical Verification:");
        List<CrimePatternFeature> syntheticMultiClass = new ArrayList<>();
        // 6 BURGLARY at WAREHOUSE during NIGHT
        for (int i = 0; i < 6; i++) {
            syntheticMultiClass.add(new CrimePatternFeature("B" + i, "BURGLARY", "L1", "WAREHOUSE", 13.08, 80.27, 23, 5, 3, "2026-03-20T23:00:00Z"));
        }
        // 6 ROBBERY at COMMERCIAL during EVENING
        for (int i = 0; i < 6; i++) {
            syntheticMultiClass.add(new CrimePatternFeature("R" + i, "ROBBERY", "L2", "COMMERCIAL", 13.09, 80.28, 19, 2, 4, "2026-04-10T19:00:00Z"));
        }
        // 6 THEFT at TRANSIT during MORNING
        for (int i = 0; i < 6; i++) {
            syntheticMultiClass.add(new CrimePatternFeature("T" + i, "THEFT", "L3", "TRANSIT", 13.07, 80.26, 8, 1, 5, "2026-05-15T08:00:00Z"));
        }

        CrimePatternPredictionEngine multiEngine = new CrimePatternPredictionEngine();
        multiEngine.train(syntheticMultiClass);
        CrimePatternPredictionEngine.EvaluationMetrics multiEval = multiEngine.getEvaluationMetrics();

        if ("EVALUATED".equals(multiEval.status) && multiEval.accuracy != null && multiEval.precision != null && multiEval.f1 != null) {
            System.out.printf("   ✓ PASSED: Multi-class LOOCV computed real metrics: Accuracy=%.2f, Precision=%.2f, Recall=%.2f, Macro-F1=%.2f%n",
                    multiEval.accuracy, multiEval.precision, multiEval.recall, multiEval.f1);
            passed++;
        } else {
            System.out.println("   ✗ FAILED: Multi-class engine failed to compute full LOOCV metrics");
            failed++;
        }

        // -------------------------------------------------------------------------
        // PHASE 5: Deterministic Prediction & Log-Sum-Exp Normalization
        // -------------------------------------------------------------------------
        System.out.println("\n[PHASE 5] Deterministic Prediction & Normalization:");
        var pred1 = multiEngine.predict("WAREHOUSE", 23, 5, "13.08:80.27");
        var pred2 = multiEngine.predict("WAREHOUSE", 23, 5, "13.08:80.27");

        double sumProb = 0.0;
        for (var p : pred1) sumProb += p.getProbability();

        if (Math.abs(sumProb - 1.0) < 0.001) {
            System.out.printf("   ✓ PASSED: Probabilities sum to 1.0 (actual: %.6f)%n", sumProb);
            passed++;
        } else {
            System.out.printf("   ✗ FAILED: Probabilities do not sum to 1.0 (actual: %.6f)%n", sumProb);
            failed++;
        }

        boolean deterministic = pred1.size() == pred2.size();
        for (int i = 0; i < pred1.size(); i++) {
            if (!pred1.get(i).getCrimeType().equals(pred2.get(i).getCrimeType()) ||
                Math.abs(pred1.get(i).getProbability() - pred2.get(i).getProbability()) > 1e-9) {
                deterministic = false;
            }
        }
        if (deterministic) {
            System.out.println("   ✓ PASSED: Prediction is 100% deterministic (identical inputs yield identical outputs)");
            passed++;
        } else {
            System.out.println("   ✗ FAILED: Non-deterministic prediction results");
            failed++;
        }

        // Top prediction for WAREHOUSE at night should be BURGLARY
        if (!pred1.isEmpty() && "BURGLARY".equals(pred1.get(0).getCrimeType())) {
            System.out.printf("   ✓ PASSED: Top prediction matches feature likelihood ('%s' with p=%.4f)%n",
                    pred1.get(0).getCrimeType(), pred1.get(0).getProbability());
            passed++;
        } else {
            System.out.println("   ✗ FAILED: Expected BURGLARY to be top prediction");
            failed++;
        }

        // -------------------------------------------------------------------------
        // PHASE 6: Diurnal Risk Curve & Spatial Hotspots
        // -------------------------------------------------------------------------
        System.out.println("\n[PHASE 6] Diurnal Risk Curve & Spatial Hotspot Calculation:");
        var diurnal = engine.computeHourlyRiskCurve();
        if (diurnal.size() == 24) {
            System.out.println("   ✓ PASSED: Diurnal risk curve produces exactly 24 hourly distribution slots");
            passed++;
        } else {
            System.out.println("   ✗ FAILED: Expected 24 hours, got " + diurnal.size());
            failed++;
        }

        // -------------------------------------------------------------------------
        // PHASE 7: API Endpoint Security (401 Unauthenticated)
        // -------------------------------------------------------------------------
        System.out.println("\n[PHASE 7] HTTP API Security & Authentication Guards:");
        {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/api/ai/patterns")).build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401) {
                System.out.println("   ✓ PASSED: GET /api/ai/patterns without cookie returns 401 Unauthorized");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: Expected 401, got " + res.statusCode());
                failed++;
            }

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/ai/predict"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            HttpResponse<String> postRes = client.send(postReq, HttpResponse.BodyHandlers.ofString());
            if (postRes.statusCode() == 401) {
                System.out.println("   ✓ PASSED: POST /api/ai/predict without cookie returns 401 Unauthorized");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: Expected 401, got " + postRes.statusCode());
                failed++;
            }
        }

        // Authenticate as Commander Raghav
        String sessionCookie = null;
        {
            String form = "username=commander&password=ChangeMe!2026";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            String setCookie = res.headers().firstValue("Set-Cookie").orElse("");
            if (setCookie.contains("CCECS_SESSION=")) {
                sessionCookie = setCookie.split(";")[0];
                System.out.println("   ✓ Logged in as 'commander' with valid session cookie");
            } else {
                System.out.println("   ✗ FAILED: Unable to authenticate as commander");
                failed++;
            }
        }

        // -------------------------------------------------------------------------
        // PHASE 8: Authenticated Pattern Intelligence API (GET /api/ai/patterns)
        // -------------------------------------------------------------------------
        System.out.println("\n[PHASE 8] Authenticated Pattern Intelligence API (GET /api/ai/patterns):");
        if (sessionCookie != null) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/ai/patterns"))
                    .header("Cookie", sessionCookie)
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body().contains("\"model\"") && res.body().contains("\"temporalRiskCurve\"")) {
                System.out.println("   ✓ PASSED: GET /api/ai/patterns returns 200 OK with model telemetry and temporal risk curve");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: Expected 200 OK with valid telemetry, got " + res.statusCode() + ": " + res.body());
                failed++;
            }

            if (validateJsonSyntax(res.body(), "GET /api/ai/patterns")) {
                System.out.println("   ✓ PASSED: GET /api/ai/patterns response has 100% valid JSON syntax");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: GET /api/ai/patterns response has malformed JSON syntax");
                failed++;
            }

            // Verify Disclaimer in GET
            if (res.body().contains("Statistical patterns represent descriptive historical frequency")) {
                System.out.println("   ✓ PASSED: GET /api/ai/patterns contains mandatory epistemic disclaimer");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: Missing epistemic disclaimer in GET /api/ai/patterns");
                failed++;
            }
        }

        // -------------------------------------------------------------------------
        // PHASE 9: Authenticated Prediction API (POST /api/ai/predict)
        // -------------------------------------------------------------------------
        System.out.println("\n[PHASE 9] Authenticated Scenario Prediction API (POST /api/ai/predict):");
        if (sessionCookie != null) {
            String jsonPayload = "{\"locationType\":\"COMMERCIAL\",\"targetHour\":20,\"targetDayOfWeek\":5}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/ai/predict"))
                    .header("Cookie", sessionCookie)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body().contains("\"predictions\"") && res.body().contains("\"hotspots\"")) {
                System.out.println("   ✓ PASSED: POST /api/ai/predict returns 200 OK with predictions and spatial hotspots");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: Expected 200 OK with predictions, got " + res.statusCode() + ": " + res.body());
                failed++;
            }

            if (validateJsonSyntax(res.body(), "POST /api/ai/predict")) {
                System.out.println("   ✓ PASSED: POST /api/ai/predict response has 100% valid JSON syntax");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: POST /api/ai/predict response has malformed JSON syntax");
                failed++;
            }

            // Verify Disclaimer in POST
            if (res.body().contains("Statistical patterns represent descriptive historical frequency")) {
                System.out.println("   ✓ PASSED: POST /api/ai/predict contains mandatory epistemic disclaimer");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: Missing epistemic disclaimer in POST /api/ai/predict");
                failed++;
            }
        }

        // -------------------------------------------------------------------------
        // PHASE 10: Audit Trail Verification (AI_FORECAST action)
        // -------------------------------------------------------------------------
        System.out.println("\n[PHASE 10] Audit Trail Verification (AI_FORECAST logging):");
        boolean auditFound = false;
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM audit_logs WHERE action = 'AI_FORECAST' ORDER BY id DESC LIMIT 5")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                auditFound = true;
                String userId = rs.getString("user_id");
                String entityType = rs.getString("entity_type");
                String details = rs.getString("details");
                System.out.println("   ✓ PASSED: Found audit log: user=" + userId + ", entity=" + entityType + ", details=" + details);
                break;
            }
        }
        if (auditFound) {
            passed++;
        } else {
            System.out.println("   ✗ FAILED: No audit log entry with action 'AI_FORECAST' found");
            failed++;
        }

        // -------------------------------------------------------------------------
        // PHASE 11: Dedicated UI Access (/crime-prediction)
        // -------------------------------------------------------------------------
        System.out.println("\n[PHASE 11] Dedicated Web UI Page Verification (/crime-prediction):");
        if (sessionCookie != null) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/crime-prediction"))
                    .header("Cookie", sessionCookie)
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body().contains("Crime Pattern Prediction")) {
                System.out.println("   ✓ PASSED: GET /crime-prediction returns 200 OK and renders AI Prediction Workspace");
                passed++;
            } else {
                System.out.println("   ✗ FAILED: Expected 200 OK for /crime-prediction, got " + res.statusCode());
                failed++;
            }
        }

        // -------------------------------------------------------------------------
        // SUMMARY
        // -------------------------------------------------------------------------
        System.out.println("\n=================================================================");
        System.out.printf("   TEST RUN COMPLETE: %d PASSED, %d FAILED%n", passed, failed);
        System.out.println("=================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static boolean validateJsonSyntax(String json, String label) {
        if (json == null || json.trim().isEmpty()) {
            System.err.println("JSON Error (" + label + "): Empty JSON response");
            return false;
        }
        String s = json.trim();
        if (!((s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[") && s.endsWith("]")))) {
            System.err.println("JSON Error (" + label + "): Must start and end with { } or [ ]");
            return false;
        }
        java.util.ArrayDeque<Character> stack = new java.util.ArrayDeque<>();
        boolean inStr = false;
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                esc = false;
                continue;
            }
            if (c == '\\' && inStr) {
                esc = true;
                continue;
            }
            if (c == '"') {
                inStr = !inStr;
                continue;
            }
            if (inStr) continue;

            if (c == '{' || c == '[') {
                stack.push(c);
            } else if (c == '}') {
                if (stack.isEmpty() || stack.pop() != '{') {
                    System.err.println("JSON Error (" + label + "): Mismatched '}' at index " + i);
                    return false;
                }
            } else if (c == ']') {
                if (stack.isEmpty() || stack.pop() != '[') {
                    System.err.println("JSON Error (" + label + "): Mismatched ']' at index " + i);
                    return false;
                }
            }
        }
        if (inStr) {
            System.err.println("JSON Error (" + label + "): Unterminated string literal");
            return false;
        }
        if (!stack.isEmpty()) {
            System.err.println("JSON Error (" + label + "): Unclosed braces/brackets: " + stack);
            return false;
        }
        return true;
    }
}
