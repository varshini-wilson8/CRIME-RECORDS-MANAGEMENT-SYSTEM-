package model;

/**
 * Scenario Request DTO for Crime Pattern Prediction (Phase 1M-AI).
 *
 * Epistemic & Ethical Boundary:
 * Strictly spatio-temporal scenario parameters only.
 * No person or suspect parameters.
 */
public final class CrimePredictionRequest {

    private final String locationId;
    private final String locationType;
    private final Double latitude;
    private final Double longitude;
    private final Integer targetHour;
    private final Integer targetDayOfWeek;
    private final Double radiusKm;

    public CrimePredictionRequest(String locationId,
                                  String locationType,
                                  Double latitude,
                                  Double longitude,
                                  Integer targetHour,
                                  Integer targetDayOfWeek,
                                  Double radiusKm) {
        this.locationId = locationId;
        this.locationType = locationType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.targetHour = targetHour;
        this.targetDayOfWeek = targetDayOfWeek;
        this.radiusKm = (radiusKm != null && radiusKm > 0.0) ? radiusKm : 5.0;
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

    public Integer getTargetHour() {
        return targetHour;
    }

    public Integer getTargetDayOfWeek() {
        return targetDayOfWeek;
    }

    public Double getRadiusKm() {
        return radiusKm;
    }
}
