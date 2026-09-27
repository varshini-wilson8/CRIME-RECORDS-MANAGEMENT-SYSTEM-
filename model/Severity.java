package model;

/**
 * Represents how serious a crime case is.
 * Used together with the number of days a case has been open
 * to compute a case's priority score.
 */
public enum Severity {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int weight;

    Severity(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
