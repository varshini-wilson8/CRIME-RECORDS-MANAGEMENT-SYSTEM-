package model;

public final class CaseEvent {
    private final long id;
    private final String caseId;
    private final String eventType;
    private final String title;
    private final String description;
    private final String eventTime;
    private final String locationName;
    private final String personName;
    private final String evidenceId;
    private final String source;
    private final String verificationStatus;
    private final String truthLevel;

    public CaseEvent(long id, String caseId, String eventType, String title, String description,
                     String eventTime, String locationName, String personName, String evidenceId,
                     String source, String verificationStatus) {
        this(id, caseId, eventType, title, description, eventTime, locationName, personName, evidenceId, source, verificationStatus, "REPORTED");
    }

    public CaseEvent(long id, String caseId, String eventType, String title, String description,
                     String eventTime, String locationName, String personName, String evidenceId,
                     String source, String verificationStatus, String truthLevel) {
        this.id = id; this.caseId = caseId; this.eventType = eventType; this.title = title;
        this.description = description; this.eventTime = eventTime; this.locationName = locationName;
        this.personName = personName; this.evidenceId = evidenceId; this.source = source;
        this.verificationStatus = verificationStatus;
        this.truthLevel = (truthLevel != null && !truthLevel.isBlank()) ? truthLevel : "REPORTED";
    }

    public long getId(){return id;}
    public String getCaseId(){return caseId;}
    public String getEventType(){return eventType;}
    public String getTitle(){return title;}
    public String getDescription(){return description;}
    public String getEventTime(){return eventTime;}
    public String getLocationName(){return locationName;}
    public String getPersonName(){return personName;}
    public String getEvidenceId(){return evidenceId;}
    public String getSource(){return source;}
    public String getVerificationStatus(){return verificationStatus;}
    public String getTruthLevel(){return truthLevel;}
}
