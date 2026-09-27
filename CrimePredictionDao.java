import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import model.CrimePatternFeature;

/**
 * Data Access Object for AI Crime Pattern Prediction (Phase 1M-AI).
 * Extracts clean historical incident observations adhering strictly to:
 * 1. Exactly one observation per historical case/incident.
 * 2. Strictly spatio-temporal features only (no person/suspect profiling, no severity).
 * 3. No artificial hour manufacturing (null when unknown).
 * 4. No artificial coordinate manufacturing (null when unknown).
 */
public final class CrimePredictionDao {

    public List<CrimePatternFeature> extractHistoricalDataset() throws SQLException {
        List<CrimePatternFeature> dataset = new ArrayList<>();

        String sql =
            "SELECT " +
            "    c.id AS case_id, " +
            "    c.crime_type, " +
            "    c.opening_date, " +
            "    cl.location_id AS case_loc_id, " +
            "    l.name AS loc_name, " +
            "    l.location_type AS loc_type, " +
            "    l.latitude AS loc_lat, " +
            "    l.longitude AS loc_lng, " +
            "    ev.event_time, " +
            "    ev.location_id AS ev_loc_id, " +
            "    ev_loc.location_type AS ev_loc_type, " +
            "    ev_loc.latitude AS ev_loc_lat, " +
            "    ev_loc.longitude AS ev_loc_lng " +
            "FROM cases c " +
            "LEFT JOIN case_locations cl ON cl.case_id = c.id AND cl.relationship_type = 'INCIDENT_LOCATION' " +
            "LEFT JOIN locations l ON l.id = cl.location_id " +
            "LEFT JOIN ( " +
            "    SELECT e1.case_id, e1.event_time, e1.location_id " +
            "    FROM case_events e1 " +
            "    WHERE e1.id = ( " +
            "        SELECT e2.id FROM case_events e2 " +
            "        WHERE e2.case_id = e1.case_id " +
            "        ORDER BY " +
            "            CASE WHEN e2.event_type = 'INCIDENT' THEN 1 " +
            "                 WHEN e2.event_type = 'CASE_OPENED' THEN 2 " +
            "                 ELSE 3 END, " +
            "            e2.event_time ASC " +
            "        LIMIT 1 " +
            "    ) " +
            ") ev ON ev.case_id = c.id " +
            "LEFT JOIN locations ev_loc ON ev_loc.id = ev.location_id " +
            "WHERE c.crime_type IS NOT NULL AND TRIM(c.crime_type) != '' " +
            "ORDER BY c.id ASC";

        try (Connection conn = Database.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String caseId = rs.getString("case_id");
                String crimeType = rs.getString("crime_type");
                String openingDate = rs.getString("opening_date");
                String eventTime = rs.getString("event_time");

                // Location resolution: incident event location overrides case_location
                String finalLocId = rs.getString("ev_loc_id");
                if (finalLocId == null) finalLocId = rs.getString("case_loc_id");

                String finalLocType = rs.getString("ev_loc_type");
                if (finalLocType == null) finalLocType = rs.getString("loc_type");

                Double finalLat = (Double) rs.getObject("ev_loc_lat");
                if (finalLat == null) finalLat = (Double) rs.getObject("loc_lat");

                Double finalLng = (Double) rs.getObject("ev_loc_lng");
                if (finalLng == null) finalLng = (Double) rs.getObject("loc_lng");

                // Temporal extraction without artificial defaults
                Integer hour = null;
                Integer dayOfWeek = null;
                Integer month = null;
                String finalTimestamp = null;

                if (eventTime != null && eventTime.contains("T")) {
                    try {
                        LocalDateTime dt = LocalDateTime.parse(eventTime);
                        hour = dt.getHour();
                        dayOfWeek = dt.getDayOfWeek().getValue() % 7; // Sunday = 0
                        month = dt.getMonthValue();
                        finalTimestamp = eventTime;
                    } catch (Exception ignored) {}
                } else if (openingDate != null) {
                    try {
                        LocalDate d = LocalDate.parse(openingDate);
                        dayOfWeek = d.getDayOfWeek().getValue() % 7;
                        month = d.getMonthValue();
                        finalTimestamp = openingDate;
                    } catch (Exception ignored) {}
                }

                dataset.add(new CrimePatternFeature(
                    caseId,
                    crimeType,
                    finalLocId,
                    finalLocType,
                    finalLat,
                    finalLng,
                    hour,
                    dayOfWeek,
                    month,
                    finalTimestamp
                ));
            }
        }
        return dataset;
    }

    public List<LocationOption> getKnownLocations() throws SQLException {
        List<LocationOption> list = new ArrayList<>();
        String sql = "SELECT id, name, address, city, location_type, latitude, longitude " +
                     "FROM locations ORDER BY name ASC";
        try (Connection conn = Database.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new LocationOption(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("address"),
                    rs.getString("city"),
                    rs.getString("location_type"),
                    (Double) rs.getObject("latitude"),
                    (Double) rs.getObject("longitude")
                ));
            }
        }
        return list;
    }

    public static final class LocationOption {
        public final String id;
        public final String name;
        public final String address;
        public final String city;
        public final String locationType;
        public final Double latitude;
        public final Double longitude;

        public LocationOption(String id, String name, String address, String city, String locationType, Double latitude, Double longitude) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.city = city;
            this.locationType = locationType;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
