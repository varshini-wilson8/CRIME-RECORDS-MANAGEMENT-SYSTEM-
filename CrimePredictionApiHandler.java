import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import model.CrimePatternFeature;

/**
 * REST API Handler for AI Crime Pattern Prediction (Phase 1M-AI).
 * Endpoints:
 * - GET  /api/ai/patterns: Returns model information, validation metrics, known locations, and baseline diurnal patterns.
 * - POST /api/ai/predict:  Generates aggregate spatio-temporal scenario predictions using the trained Categorical Naive Bayes engine.
 *
 * Epistemic & Ethical Invariants:
 * - Aggregate crime patterns only.
 * - Zero suspect or person profiling.
 * - Mandatory epistemic disclaimer in every response.
 */
public final class CrimePredictionApiHandler implements HttpHandler {

    private final CrimePatternPredictionEngine engine;
    private final CrimePredictionDao dao;
    private final AuditLogDao audit;
    private final SessionManager sessions;

    private static final String DISCLAIMER =
        "Statistical patterns represent descriptive historical frequency distributions across reported incidents. " +
        "Forecasts do not imply individualized suspicion, certainty of occurrence, or automated legal culpability.";

    public CrimePredictionApiHandler(CrimePatternPredictionEngine engine,
                                    CrimePredictionDao dao,
                                    AuditLogDao audit,
                                    SessionManager sessions) {
        this.engine = engine;
        this.dao = dao;
        this.audit = audit;
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        String path = exchange.getRequestURI().getPath();

        if ("GET".equals(method)) {
            handleGetPatterns(exchange);
        } else if ("POST".equals(method)) {
            handlePostPredict(exchange);
        } else {
            HttpUtil.text(exchange, 405, "{\"error\":\"Method not allowed\"}", "application/json");
        }
    }

    private void handleGetPatterns(HttpExchange exchange) throws IOException {
        try {
            List<CrimePredictionDao.LocationOption> locations = dao.getKnownLocations();
            CrimePatternPredictionEngine.EvaluationMetrics eval = engine.getEvaluationMetrics();
            List<CrimePatternPredictionEngine.HourlyRisk> hourlyCurve = engine.computeHourlyRiskCurve();

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"model\":\"").append(q(engine.getModelVersion())).append("\",");
            sb.append("\"state\":\"").append(q(engine.getState().name())).append("\",");
            sb.append("\"sampleSize\":").append(engine.getTotalSamples()).append(',');
            sb.append("\"dataSupportLevel\":\"").append(getDataSupportLevel(engine.getTotalSamples())).append("\",");

            // Distinct classes
            sb.append("\"crimeClasses\":[");
            int cIdx = 0;
            for (String c : engine.getDistinctClasses()) {
                if (cIdx++ > 0) sb.append(',');
                sb.append('"').append(q(c)).append('"');
            }
            sb.append("],");

            // Evaluation Metrics
            sb.append("\"evaluation\":{");
            sb.append("\"status\":\"").append(q(eval.status)).append("\",");
            sb.append("\"accuracy\":").append(eval.accuracy != null ? eval.accuracy : "null").append(',');
            sb.append("\"precision\":").append(eval.precision != null ? eval.precision : "null").append(',');
            sb.append("\"recall\":").append(eval.recall != null ? eval.recall : "null").append(',');
            sb.append("\"f1\":").append(eval.f1 != null ? eval.f1 : "null").append(',');
            sb.append("\"method\":\"").append(q(eval.evaluationMethod)).append("\",");
            sb.append("\"evaluationDate\":\"").append(q(eval.evaluationDate)).append("\",");
            sb.append("\"notice\":\"").append(q(eval.evaluationNotice)).append("\"");
            sb.append("},");

            // Known Locations for Scenario Picker
            sb.append("\"locations\":[");
            for (int i = 0; i < locations.size(); i++) {
                if (i > 0) sb.append(',');
                CrimePredictionDao.LocationOption loc = locations.get(i);
                sb.append("{\"id\":\"").append(q(loc.id)).append("\",")
                  .append("\"name\":\"").append(q(loc.name)).append("\",")
                  .append("\"address\":\"").append(q(loc.address)).append("\",")
                  .append("\"city\":\"").append(q(loc.city)).append("\",")
                  .append("\"locationType\":\"").append(q(loc.locationType)).append("\",")
                  .append("\"latitude\":").append(loc.latitude != null ? loc.latitude : "null").append(',')
                  .append("\"longitude\":").append(loc.longitude != null ? loc.longitude : "null").append('}');
            }
            sb.append("],");

            // Diurnal Curve
            sb.append("\"temporalRiskCurve\":[");
            for (int i = 0; i < hourlyCurve.size(); i++) {
                if (i > 0) sb.append(',');
                CrimePatternPredictionEngine.HourlyRisk hr = hourlyCurve.get(i);
                sb.append("{\"hour\":").append(hr.hour).append(',')
                  .append("\"count\":").append(hr.historicalCount).append(',')
                  .append("\"probability\":").append(hr.probability).append('}');
            }
            sb.append("],");

            // Hotspots
            List<Map<String, Object>> hotspots = computeNearbyHotspots(null, null);
            sb.append("\"hotspots\":[");
            for (int i = 0; i < hotspots.size(); i++) {
                if (i > 0) sb.append(',');
                Map<String, Object> h = hotspots.get(i);
                sb.append("{\"id\":\"").append(q((String) h.get("id"))).append("\",")
                  .append("\"name\":\"").append(q((String) h.get("name"))).append("\",")
                  .append("\"latitude\":").append(h.get("latitude")).append(',')
                  .append("\"longitude\":").append(h.get("longitude")).append(',')
                  .append("\"distanceKm\":").append(h.get("distanceKm")).append(',')
                  .append("\"riskDensity\":").append(h.get("riskDensity")).append('}');
            }
            sb.append("],");

            sb.append("\"disclaimer\":\"").append(q(DISCLAIMER)).append("\"");
            sb.append('}');

            HttpUtil.text(exchange, 200, sb.toString(), "application/json");
        } catch (Exception e) {
            HttpUtil.text(exchange, 500, "{\"error\":\"" + q(e.getMessage()) + "\"}", "application/json");
        }
    }

    private void handlePostPredict(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseParameters(body, exchange.getRequestURI().getQuery());

        String locationId = params.get("locationId");
        String locationType = params.get("locationType");
        String targetHourStr = params.get("targetHour");
        String targetDayStr = params.get("targetDayOfWeek");
        String latStr = params.get("latitude");
        String lngStr = params.get("longitude");

        Integer targetHour = null;
        if (targetHourStr != null && !targetHourStr.isBlank()) {
            try {
                int h = Integer.parseInt(targetHourStr.trim());
                if (h >= 0 && h < 24) targetHour = h;
            } catch (NumberFormatException ignored) {}
        }

        Integer targetDayOfWeek = null;
        if (targetDayStr != null && !targetDayStr.isBlank()) {
            try {
                int d = Integer.parseInt(targetDayStr.trim());
                if (d >= 0 && d <= 6) targetDayOfWeek = d;
            } catch (NumberFormatException ignored) {}
        }

        Double latitude = null;
        Double longitude = null;
        if (latStr != null && !latStr.isBlank()) {
            try { latitude = Double.parseDouble(latStr.trim()); } catch (NumberFormatException ignored) {}
        }
        if (lngStr != null && !lngStr.isBlank()) {
            try { longitude = Double.parseDouble(lngStr.trim()); } catch (NumberFormatException ignored) {}
        }

        // If locationId provided, lookup coordinates and locationType if missing
        String resolvedLocName = null;
        try {
            if (locationId != null && !locationId.isBlank()) {
                List<CrimePredictionDao.LocationOption> locs = dao.getKnownLocations();
                for (CrimePredictionDao.LocationOption loc : locs) {
                    if (loc.id.equalsIgnoreCase(locationId.trim())) {
                        resolvedLocName = loc.name;
                        if (locationType == null || locationType.isBlank()) {
                            locationType = loc.locationType;
                        }
                        if (latitude == null) latitude = loc.latitude;
                        if (longitude == null) longitude = loc.longitude;
                        break;
                    }
                }
            }
        } catch (SQLException ignored) {}

        String spatialGrid = null;
        if (latitude != null && longitude != null) {
            double latRound = Math.round(latitude * 50.0) / 50.0;
            double lngRound = Math.round(longitude * 50.0) / 50.0;
            spatialGrid = String.format(Locale.US, "%.2f:%.2f", latRound, lngRound);
        }

        // Run Categorical Naive Bayes Prediction
        List<CrimePatternPredictionEngine.PredictionEntry> predictions =
            engine.predict(locationType, targetHour, targetDayOfWeek, spatialGrid);

        // Compute Spatial Hotspot Proximity
        List<Map<String, Object>> hotspots = computeNearbyHotspots(latitude, longitude);

        // Compute Diurnal Hourly Risk Curve
        List<CrimePatternPredictionEngine.HourlyRisk> hourlyCurve = engine.computeHourlyRiskCurve();

        // Audit Logging
        String token = HttpUtil.cookie(exchange, "CCECS_SESSION");
        if (token != null) {
            sessions.get(token).ifPresent(user -> {
                try {
                    String details = "Scenario query: loc=" + locationId + ", hour=" + targetHourStr + ", day=" + targetDayStr;
                    audit.record(user.getUserId(), "AI_FORECAST", "MODEL", "CRIME_PATTERN", details);
                } catch (SQLException ignored) {}
            });
        }

        CrimePatternPredictionEngine.EvaluationMetrics eval = engine.getEvaluationMetrics();

        // Format Response JSON
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"forecastType\":\"AGGREGATE_CRIME_PATTERN\",");
        sb.append("\"modelVersion\":\"").append(q(engine.getModelVersion())).append("\",");
        sb.append("\"sampleSize\":").append(engine.getTotalSamples()).append(',');
        sb.append("\"dataSupportLevel\":\"").append(getDataSupportLevel(engine.getTotalSamples())).append("\",");

        // Primary predicted crime type
        String primaryType = !predictions.isEmpty() ? predictions.get(0).getCrimeType() : "INSUFFICIENT_DATA";
        sb.append("\"primaryPredictedType\":\"").append(q(primaryType)).append("\",");

        // Resolved Scenario Context
        sb.append("\"scenario\":{");
        sb.append("\"locationId\":").append(locationId != null ? "\"" + q(locationId) + "\"" : "null").append(',');
        sb.append("\"locationName\":").append(resolvedLocName != null ? "\"" + q(resolvedLocName) + "\"" : "null").append(',');
        sb.append("\"locationType\":").append(locationType != null ? "\"" + q(locationType) + "\"" : "null").append(',');
        sb.append("\"targetHour\":").append(targetHour != null ? targetHour : "null").append(',');
        sb.append("\"targetDayOfWeek\":").append(targetDayOfWeek != null ? targetDayOfWeek : "null").append(',');
        sb.append("\"spatialGrid\":").append(spatialGrid != null ? "\"" + q(spatialGrid) + "\"" : "null");
        sb.append("},");

        // Predictions List
        sb.append("\"predictions\":[");
        for (int i = 0; i < predictions.size(); i++) {
            if (i > 0) sb.append(',');
            CrimePatternPredictionEngine.PredictionEntry p = predictions.get(i);
            sb.append("{\"crimeType\":\"").append(q(p.getCrimeType())).append("\",")
              .append("\"probability\":").append(p.getProbability()).append(',')
              .append("\"percentage\":").append(p.getPercentage()).append('}');
        }
        sb.append("],");

        // Spatial Hotspots
        sb.append("\"hotspots\":[");
        for (int i = 0; i < hotspots.size(); i++) {
            if (i > 0) sb.append(',');
            Map<String, Object> h = hotspots.get(i);
            sb.append("{\"id\":\"").append(q((String) h.get("id"))).append("\",")
              .append("\"name\":\"").append(q((String) h.get("name"))).append("\",")
              .append("\"latitude\":").append(h.get("latitude")).append(',')
              .append("\"longitude\":").append(h.get("longitude")).append(',')
              .append("\"distanceKm\":").append(h.get("distanceKm")).append(',')
              .append("\"riskDensity\":").append(h.get("riskDensity")).append('}');
        }
        sb.append("],");
        sb.append("\"spatialHotspots\":[");
        for (int i = 0; i < hotspots.size(); i++) {
            if (i > 0) sb.append(',');
            Map<String, Object> h = hotspots.get(i);
            sb.append("{\"id\":\"").append(q((String) h.get("id"))).append("\",")
              .append("\"name\":\"").append(q((String) h.get("name"))).append("\",")
              .append("\"latitude\":").append(h.get("latitude")).append(',')
              .append("\"longitude\":").append(h.get("longitude")).append(',')
              .append("\"distanceKm\":").append(h.get("distanceKm")).append(',')
              .append("\"riskDensity\":").append(h.get("riskDensity")).append('}');
        }
        sb.append("],");

        // Diurnal Curve
        sb.append("\"temporalRiskCurve\":[");
        for (int i = 0; i < hourlyCurve.size(); i++) {
            if (i > 0) sb.append(',');
            CrimePatternPredictionEngine.HourlyRisk hr = hourlyCurve.get(i);
            sb.append("{\"hour\":").append(hr.hour).append(',')
              .append("\"count\":").append(hr.historicalCount).append(',')
              .append("\"probability\":").append(hr.probability).append('}');
        }
        sb.append("],");

        // Evaluation
        sb.append("\"evaluation\":{");
        sb.append("\"status\":\"").append(q(eval.status)).append("\",");
        sb.append("\"accuracy\":").append(eval.accuracy != null ? eval.accuracy : "null").append(',');
        sb.append("\"precision\":").append(eval.precision != null ? eval.precision : "null").append(',');
        sb.append("\"recall\":").append(eval.recall != null ? eval.recall : "null").append(',');
        sb.append("\"f1\":").append(eval.f1 != null ? eval.f1 : "null").append(',');
        sb.append("\"method\":\"").append(q(eval.evaluationMethod)).append("\",");
        sb.append("\"evaluationDate\":\"").append(q(eval.evaluationDate)).append("\",");
        sb.append("\"notice\":\"").append(q(eval.evaluationNotice)).append("\"");
        sb.append("},");

        sb.append("\"disclaimer\":\"").append(q(DISCLAIMER)).append("\"");
        sb.append('}');

        HttpUtil.text(exchange, 200, sb.toString(), "application/json");
    }

    private List<Map<String, Object>> computeNearbyHotspots(Double targetLat, Double targetLng) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            List<CrimePredictionDao.LocationOption> locs = dao.getKnownLocations();
            for (CrimePredictionDao.LocationOption l : locs) {
                if (l.latitude == null || l.longitude == null) continue;

                double dist = 0.0;
                if (targetLat != null && targetLng != null) {
                    dist = haversineKm(targetLat, targetLng, l.latitude, l.longitude);
                }

                // Distance-decay density index
                double density = Math.min(100.0, Math.max(10.0, Math.round(100.0 / (1.0 + (dist * 0.5)))));

                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", l.id);
                map.put("name", l.name);
                map.put("latitude", l.latitude);
                map.put("longitude", l.longitude);
                map.put("distanceKm", Math.round(dist * 10.0) / 10.0);
                map.put("riskDensity", density);
                list.add(map);
            }
            list.sort((a, b) -> Double.compare((Double) b.get("riskDensity"), (Double) a.get("riskDensity")));
        } catch (SQLException ignored) {}
        return list;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private static String getDataSupportLevel(int n) {
        if (n < 10) return "LIMITED";
        if (n < 30) return "MODERATE";
        return "ROBUST";
    }

    private Map<String, String> parseParameters(String body, String query) {
        Map<String, String> out = new HashMap<>();
        // Query parameters
        if (query != null && !query.isBlank()) {
            for (String pair : query.split("&")) {
                String[] p = pair.split("=", 2);
                if (p.length > 0) {
                    out.put(URLDecoder.decode(p[0], StandardCharsets.UTF_8),
                            p.length == 2 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : "");
                }
            }
        }
        // Body parameters (JSON or form)
        if (body != null && !body.isBlank()) {
            String trimmed = body.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                // Lightweight JSON string parser
                String content = trimmed.substring(1, trimmed.length() - 1);
                for (String token : content.split(",")) {
                    String[] kv = token.split(":", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim().replace("\"", "");
                        String val = kv[1].trim().replace("\"", "");
                        if (!"null".equalsIgnoreCase(val)) {
                            out.put(key, val);
                        }
                    }
                }
            } else {
                for (String pair : trimmed.split("&")) {
                    String[] p = pair.split("=", 2);
                    if (p.length > 0) {
                        out.put(URLDecoder.decode(p[0], StandardCharsets.UTF_8),
                                p.length == 2 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : "");
                    }
                }
            }
        }
        return out;
    }

    private static String q(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
