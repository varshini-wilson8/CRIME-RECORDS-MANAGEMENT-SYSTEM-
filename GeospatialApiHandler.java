import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * REST API Handler for Geospatial and Temporal Intelligence (Phase 1M).
 * Serves geocoded crime locations, 24-hour hourly histograms, day-of-week patterns,
 * recurring locations, and spatiotemporal review signals.
 */
public final class GeospatialApiHandler implements HttpHandler {
    private final GeospatialDao geoDao;
    private final AuditLogDao auditDao;
    private final SessionManager sessionManager;

    public GeospatialApiHandler(GeospatialDao geoDao, AuditLogDao auditDao, SessionManager sessionManager) {
        this.geoDao = geoDao;
        this.auditDao = auditDao;
        this.sessionManager = sessionManager;
    }

    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.text(exchange, 405, "{\"error\":\"Method not allowed\"}", "application/json");
            return;
        }

        try {
            // Optional audit logging of geospatial query
            String token = HttpUtil.cookie(exchange, "CCECS_SESSION");
            if (token != null) {
                sessionManager.get(token).ifPresent(user -> {
                    try {
                        auditDao.record(user.getUserId(), "VIEW_GEOSPATIAL", "SYSTEM", "MAP", "Investigator accessed Geospatial & Temporal Intelligence map.");
                    } catch (SQLException ignored) {}
                });
            }

            List<GeospatialDao.GeoPoint> locations = geoDao.getIncidentLocations();
            Map<Integer, Integer> hourly = geoDao.getHourlyEventDistribution();
            Map<String, Integer> daily = geoDao.getDayOfWeekDistribution();
            List<GeospatialDao.RecurringLocation> recurring = geoDao.getRecurringLocations();
            List<GeospatialDao.SpatiotemporalSignal> signals = geoDao.getSpatiotemporalSignals();

            StringBuilder json = new StringBuilder("{");

            // 1. Locations
            json.append("\"locations\":[");
            for (int i = 0; i < locations.size(); i++) {
                if (i > 0) json.append(',');
                GeospatialDao.GeoPoint loc = locations.get(i);
                json.append("{\"id\":\"").append(q(loc.id)).append("\",")
                    .append("\"name\":\"").append(q(loc.name)).append("\",")
                    .append("\"address\":\"").append(q(loc.address)).append("\",")
                    .append("\"city\":\"").append(q(loc.city)).append("\",")
                    .append("\"latitude\":").append(loc.latitude).append(',')
                    .append("\"longitude\":").append(loc.longitude).append(',')
                    .append("\"locationType\":\"").append(q(loc.locationType)).append("\",")
                    .append("\"severity\":\"").append(q(loc.getHighestSeverity())).append("\",")
                    .append("\"caseCount\":").append(loc.cases.size()).append(',')
                    .append("\"cases\":[");
                for (int c = 0; c < loc.cases.size(); c++) {
                    if (c > 0) json.append(',');
                    GeospatialDao.CaseLink cl = loc.cases.get(c);
                    json.append("{\"caseId\":\"").append(q(cl.caseId)).append("\",")
                        .append("\"title\":\"").append(q(cl.title)).append("\",")
                        .append("\"severity\":\"").append(q(cl.severity)).append("\",")
                        .append("\"status\":\"").append(q(cl.status)).append("\",")
                        .append("\"relationshipType\":\"").append(q(cl.relationType)).append("\"}");
                }
                json.append("]}");
            }
            json.append("],");

            // 2. Hourly distribution
            json.append("\"hourlyPatterns\":[");
            int hIdx = 0;
            for (Map.Entry<Integer, Integer> e : hourly.entrySet()) {
                if (hIdx++ > 0) json.append(',');
                json.append("{\"hour\":").append(e.getKey()).append(",\"count\":").append(e.getValue()).append('}');
            }
            json.append("],");

            // 3. Day of week distribution
            json.append("\"dayOfWeekPatterns\":[");
            int dIdx = 0;
            for (Map.Entry<String, Integer> e : daily.entrySet()) {
                if (dIdx++ > 0) json.append(',');
                json.append("{\"day\":\"").append(q(e.getKey())).append("\",\"count\":").append(e.getValue()).append('}');
            }
            json.append("],");

            // 4. Recurring locations
            json.append("\"recurringLocations\":[");
            for (int i = 0; i < recurring.size(); i++) {
                if (i > 0) json.append(',');
                GeospatialDao.RecurringLocation rl = recurring.get(i);
                json.append("{\"id\":\"").append(q(rl.id)).append("\",")
                    .append("\"name\":\"").append(q(rl.name)).append("\",")
                    .append("\"address\":\"").append(q(rl.address)).append("\",")
                    .append("\"locationType\":\"").append(q(rl.locationType)).append("\",")
                    .append("\"caseCount\":").append(rl.caseCount).append(',')
                    .append("\"caseIds\":\"").append(q(rl.caseIds)).append("\",")
                    .append("\"note\":\"Location is referenced across multiple recorded investigations.\"}") ;
            }
            json.append("],");

            // 5. Spatiotemporal proximity signals
            json.append("\"proximitySignals\":[");
            for (int i = 0; i < signals.size(); i++) {
                if (i > 0) json.append(',');
                GeospatialDao.SpatiotemporalSignal s = signals.get(i);
                json.append("{\"case1\":\"").append(q(s.case1)).append("\",")
                    .append("\"title1\":\"").append(q(s.title1)).append("\",")
                    .append("\"time1\":\"").append(q(s.time1)).append("\",")
                    .append("\"case2\":\"").append(q(s.case2)).append("\",")
                    .append("\"title2\":\"").append(q(s.title2)).append("\",")
                    .append("\"time2\":\"").append(q(s.time2)).append("\",")
                    .append("\"locationName\":\"").append(q(s.locationName)).append("\",")
                    .append("\"hoursApart\":").append(String.format(Locale.US, "%.1f", s.hoursApart)).append(',')
                    .append("\"reviewNote\":\"Events occur within 48 hours at this location. Review independent facts; proximity alone does not establish a connection.\"}") ;
            }
            json.append("]}");

            HttpUtil.text(exchange, 200, json.toString(), "application/json");
        } catch (Exception ex) {
            ex.printStackTrace();
            HttpUtil.text(exchange, 500, "{\"error\":\"Unable to load geospatial and temporal intelligence\"}", "application/json");
        }
    }

    private static String q(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
