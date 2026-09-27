import java.sql.*;
import java.util.*;

/**
 * Data Access Object for Geospatial and Temporal Intelligence (Phase 1M).
 * Provides geocoded incident clusters, temporal histograms (hourly and day-of-week),
 * and reviewable spatiotemporal proximity signals.
 *
 * Strictly adheres to the investigative principle:
 * Signals are descriptive observations for investigator review;
 * geographic or temporal proximity does not establish criminal connection or guilt.
 */
public final class GeospatialDao {

    public List<GeoPoint> getIncidentLocations() throws SQLException {
        List<GeoPoint> points = new ArrayList<>();
        String sql = "SELECT l.id, l.name, l.address, l.city, l.latitude, l.longitude, l.location_type, " +
                     "c.id AS case_id, c.title AS case_title, c.severity, c.status, cl.relationship_type " +
                     "FROM locations l " +
                     "JOIN case_locations cl ON cl.location_id = l.id " +
                     "JOIN cases c ON c.id = cl.case_id " +
                     "WHERE l.latitude IS NOT NULL AND l.longitude IS NOT NULL " +
                     "ORDER BY l.id, c.severity";

        try (Connection conn = Database.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            Map<String, GeoPoint> map = new LinkedHashMap<>();
            while (rs.next()) {
                String locId = rs.getString("id");
                GeoPoint point = map.computeIfAbsent(locId, k -> {
                    try {
                        return new GeoPoint(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("address"),
                            rs.getString("city"),
                            rs.getDouble("latitude"),
                            rs.getDouble("longitude"),
                            rs.getString("location_type")
                        );
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                point.addCase(
                    rs.getString("case_id"),
                    rs.getString("case_title"),
                    rs.getString("severity"),
                    rs.getString("status"),
                    rs.getString("relationship_type")
                );
            }
            points.addAll(map.values());
        }
        return points;
    }

    public Map<Integer, Integer> getHourlyEventDistribution() throws SQLException {
        Map<Integer, Integer> hourly = new TreeMap<>();
        for (int i = 0; i < 24; i++) hourly.put(i, 0);

        String sql = "SELECT CAST(strftime('%H', event_time) AS INTEGER) AS hr, COUNT(*) AS cnt " +
                     "FROM case_events WHERE event_time IS NOT NULL AND length(event_time) >= 13 " +
                     "GROUP BY hr";

        try (Connection conn = Database.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int hr = rs.getInt("hr");
                if (hr >= 0 && hr < 24) {
                    hourly.put(hr, rs.getInt("cnt"));
                }
            }
        }
        return hourly;
    }

    public Map<String, Integer> getDayOfWeekDistribution() throws SQLException {
        // SQLite %w: 0 = Sunday, 1 = Monday ... 6 = Saturday
        String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        Map<String, Integer> dayMap = new LinkedHashMap<>();
        for (String d : days) dayMap.put(d, 0);

        String sql = "SELECT strftime('%w', event_time) AS day_idx, COUNT(*) AS cnt " +
                     "FROM case_events WHERE event_time IS NOT NULL " +
                     "GROUP BY day_idx";

        try (Connection conn = Database.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String idxStr = rs.getString("day_idx");
                if (idxStr != null) {
                    try {
                        int idx = Integer.parseInt(idxStr);
                        if (idx >= 0 && idx < 7) {
                            dayMap.put(days[idx], rs.getInt("cnt"));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return dayMap;
    }

    public List<RecurringLocation> getRecurringLocations() throws SQLException {
        List<RecurringLocation> list = new ArrayList<>();
        String sql = "SELECT l.id, l.name, l.address, l.location_type, " +
                     "COUNT(DISTINCT cl.case_id) AS case_count, " +
                     "group_concat(DISTINCT cl.case_id) AS case_ids " +
                     "FROM locations l " +
                     "JOIN case_locations cl ON cl.location_id = l.id " +
                     "GROUP BY l.id " +
                     "HAVING COUNT(DISTINCT cl.case_id) > 1 " +
                     "ORDER BY case_count DESC";

        try (Connection conn = Database.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new RecurringLocation(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("address"),
                    rs.getString("location_type"),
                    rs.getInt("case_count"),
                    rs.getString("case_ids")
                ));
            }
        }
        return list;
    }

    public List<SpatiotemporalSignal> getSpatiotemporalSignals() throws SQLException {
        List<SpatiotemporalSignal> signals = new ArrayList<>();
        // Cross-case events occurring within 48 hours at related or linked locations
        String sql = "SELECT e1.case_id AS case1, e1.title AS title1, e1.event_time AS time1, " +
                     "e2.case_id AS case2, e2.title AS title2, e2.event_time AS time2, " +
                     "l.name AS location_name, l.latitude, l.longitude, " +
                     "ABS(julianday(e1.event_time) - julianday(e2.event_time)) * 24.0 AS hours_apart " +
                     "FROM case_events e1 " +
                     "JOIN case_events e2 ON e1.case_id < e2.case_id " +
                     "JOIN locations l ON l.id = e1.location_id AND l.id = e2.location_id " +
                     "WHERE e1.location_id IS NOT NULL " +
                     "AND ABS(julianday(e1.event_time) - julianday(e2.event_time)) * 24.0 <= 48.0 " +
                     "ORDER BY hours_apart ASC LIMIT 20";

        try (Connection conn = Database.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                signals.add(new SpatiotemporalSignal(
                    rs.getString("case1"),
                    rs.getString("title1"),
                    rs.getString("time1"),
                    rs.getString("case2"),
                    rs.getString("title2"),
                    rs.getString("time2"),
                    rs.getString("location_name"),
                    rs.getDouble("hours_apart")
                ));
            }
        }
        return signals;
    }

    // --- Inner Models ---

    public static final class GeoPoint {
        public final String id, name, address, city, locationType;
        public final double latitude, longitude;
        public final List<CaseLink> cases = new ArrayList<>();

        public GeoPoint(String id, String name, String address, String city, double latitude, double longitude, String locationType) {
            this.id = id; this.name = name; this.address = address; this.city = city;
            this.latitude = latitude; this.longitude = longitude; this.locationType = locationType;
        }

        public void addCase(String caseId, String title, String severity, String status, String relationType) {
            cases.add(new CaseLink(caseId, title, severity, status, relationType));
        }

        public String getHighestSeverity() {
            for (CaseLink c : cases) {
                if ("CRITICAL".equalsIgnoreCase(c.severity)) return "CRITICAL";
            }
            for (CaseLink c : cases) {
                if ("HIGH".equalsIgnoreCase(c.severity)) return "HIGH";
            }
            for (CaseLink c : cases) {
                if ("MEDIUM".equalsIgnoreCase(c.severity)) return "MEDIUM";
            }
            return "LOW";
        }
    }

    public static final class CaseLink {
        public final String caseId, title, severity, status, relationType;
        public CaseLink(String caseId, String title, String severity, String status, String relationType) {
            this.caseId = caseId; this.title = title; this.severity = severity;
            this.status = status; this.relationType = relationType;
        }
    }

    public static final class RecurringLocation {
        public final String id, name, address, locationType, caseIds;
        public final int caseCount;
        public RecurringLocation(String id, String name, String address, String locationType, int caseCount, String caseIds) {
            this.id = id; this.name = name; this.address = address; this.locationType = locationType;
            this.caseCount = caseCount; this.caseIds = caseIds;
        }
    }

    public static final class SpatiotemporalSignal {
        public final String case1, title1, time1, case2, title2, time2, locationName;
        public final double hoursApart;
        public SpatiotemporalSignal(String case1, String title1, String time1, String case2, String title2, String time2, String locationName, double hoursApart) {
            this.case1 = case1; this.title1 = title1; this.time1 = time1;
            this.case2 = case2; this.title2 = title2; this.time2 = time2;
            this.locationName = locationName; this.hoursApart = hoursApart;
        }
    }
}
