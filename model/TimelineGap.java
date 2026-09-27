package model;

public final class TimelineGap {
    private final String fromTime;
    private final String toTime;
    private final long durationMinutes;
    private final String windowType;
    private final int thresholdMinutes;
    private final String label;
    private final String disclaimer;
    private final String suggestedTaskTitle;
    private final String suggestedTaskDescription;
    private final String suggestedTaskPriority;

    public TimelineGap(String fromTime, String toTime, long durationMinutes, String windowType,
                       int thresholdMinutes, String label, String disclaimer,
                       String suggestedTaskTitle, String suggestedTaskDescription, String suggestedTaskPriority) {
        this.fromTime = fromTime;
        this.toTime = toTime;
        this.durationMinutes = durationMinutes;
        this.windowType = windowType;
        this.thresholdMinutes = thresholdMinutes;
        this.label = label;
        this.disclaimer = disclaimer;
        this.suggestedTaskTitle = suggestedTaskTitle;
        this.suggestedTaskDescription = suggestedTaskDescription;
        this.suggestedTaskPriority = suggestedTaskPriority;
    }

    public String getFromTime() { return fromTime; }
    public String getToTime() { return toTime; }
    public long getDurationMinutes() { return durationMinutes; }
    public String getWindowType() { return windowType; }
    public int getThresholdMinutes() { return thresholdMinutes; }
    public String getLabel() { return label; }
    public String getDisclaimer() { return disclaimer; }
    public String getSuggestedTaskTitle() { return suggestedTaskTitle; }
    public String getSuggestedTaskDescription() { return suggestedTaskDescription; }
    public String getSuggestedTaskPriority() { return suggestedTaskPriority; }
}
