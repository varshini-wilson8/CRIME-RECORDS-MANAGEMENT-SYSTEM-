package model;

import java.util.Locale;
import java.util.Objects;

/**
 * Historical Crime Pattern Feature Vector (Phase 1M-AI).
 * Represents a single historical crime incident observation.
 *
 * Epistemic & Ethical Boundary:
 * Contains only aggregate spatio-temporal features.
 * Strictly excludes suspect, person, demographic, and risk-level attributes.
 */
public final class CrimePatternFeature {

    private final String caseId;
    private final String crimeType;
    private final String locationId;
    private final String locationType;
    private final Double latitude;
    private final Double longitude;
    private final Integer hourOfDay;   // 0 - 23, null if unknown
    private final Integer dayOfWeek;   // 0 = Sunday, 1 = Monday ... 6 = Saturday, null if unknown
    private final Integer monthOfYear; // 1 - 12, null if unknown
    private final String timestamp;    // ISO timestamp or date string, null if unknown

    public CrimePatternFeature(String caseId,
                               String crimeType,
                               String locationId,
                               String locationType,
                               Double latitude,
                               Double longitude,
                               Integer hourOfDay,
                               Integer dayOfWeek,
                               Integer monthOfYear,
                               String timestamp) {
        this.caseId = Objects.requireNonNull(caseId, "caseId must not be null");
        this.crimeType = Objects.requireNonNull(crimeType, "crimeType must not be null").toUpperCase(Locale.ROOT);
        this.locationId = locationId;
        this.locationType = locationType != null ? locationType.toUpperCase(Locale.ROOT) : "UNKNOWN";
        this.latitude = latitude;
        this.longitude = longitude;
        this.hourOfDay = hourOfDay;
        this.dayOfWeek = dayOfWeek;
        this.monthOfYear = monthOfYear;
        this.timestamp = timestamp;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getCrimeType() {
        return crimeType;
    }

    public String getLocationId() {
        return locationId;
    }

    public String getLocationType() {
        return locationType;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Integer getHourOfDay() {
        return hourOfDay;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public Integer getMonthOfYear() {
        return monthOfYear;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public boolean hasValidCoordinates() {
        return latitude != null && longitude != null && !Double.isNaN(latitude) && !Double.isNaN(longitude);
    }

    public boolean hasValidHour() {
        return hourOfDay != null && hourOfDay >= 0 && hourOfDay < 24;
    }

    /**
     * Categorical time slot partition with midnight boundary:
     * NIGHT: 22:00 - 05:59 (22, 23, 0, 1, 2, 3, 4, 5)
     * MORNING: 06:00 - 11:59 (6, 7, 8, 9, 10, 11)
     * AFTERNOON: 12:00 - 16:59 (12, 13, 14, 15, 16)
     * EVENING: 17:00 - 21:59 (17, 18, 19, 20, 21)
     */
    public String getTimeSlot() {
        if (!hasValidHour()) return "UNKNOWN";
        int h = hourOfDay;
        if (h >= 22 || h <= 5) return "NIGHT";
        if (h <= 11) return "MORNING";
        if (h <= 16) return "AFTERNOON";
        return "EVENING";
    }

    /**
     * Day of week partition:
     * WEEKEND: Sunday (0) and Saturday (6)
     * WEEKDAY: Monday (1) through Friday (5)
     */
    public String getDayType() {
        if (dayOfWeek == null) return "UNKNOWN";
        return (dayOfWeek == 0 || dayOfWeek == 6) ? "WEEKEND" : "WEEKDAY";
    }

    /**
     * Spatial grid cell key rounded to 0.02 degrees (~2.2 km lat, ~2.0 km lng).
     * Provides analytical aggregation bin without implying exact predictive boundaries.
     */
    public String getSpatialGridKey() {
        if (!hasValidCoordinates()) return "UNMAPPED";
        double latRound = Math.round(latitude * 50.0) / 50.0;
        double lngRound = Math.round(longitude * 50.0) / 50.0;
        return String.format(Locale.US, "%.2f:%.2f", latRound, lngRound);
    }

    @Override
    public String toString() {
        return "CrimePatternFeature{" +
                "caseId='" + caseId + '\'' +
                ", crimeType='" + crimeType + '\'' +
                ", locationType='" + locationType + '\'' +
                ", timeSlot='" + getTimeSlot() + '\'' +
                ", dayType='" + getDayType() + '\'' +
                ", grid='" + getSpatialGridKey() + '\'' +
                '}';
    }
}
