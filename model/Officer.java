package model;

/**
 * Represents a police officer who can be assigned crime cases.
 * Demonstrates INHERITANCE (extends Person) and POLYMORPHISM
 * (overrides getRole() and toString()).
 */
public class Officer extends Person {

    private String badgeNumber;
    private int activeCaseCount;

    public Officer(String id, String name, String contact, String badgeNumber) {
        super(id, name, contact);
        this.badgeNumber = badgeNumber;
        this.activeCaseCount = 0;
    }

    public Officer(String id, String name, String contact, String badgeNumber, int activeCaseCount) {
        super(id, name, contact);
        this.badgeNumber = badgeNumber;
        this.activeCaseCount = activeCaseCount;
    }

    @Override
    public String getRole() {
        return "Officer";
    }

    // ----- Convenience methods for workload auto-assignment -----

    public void incrementActiveCaseCount() {
        this.activeCaseCount++;
    }

    public void decrementActiveCaseCount() {
        if (this.activeCaseCount > 0) {
            this.activeCaseCount--;
        }
    }

    // ----- Getters and Setters -----

    public String getBadgeNumber() {
        return badgeNumber;
    }

    public void setBadgeNumber(String badgeNumber) {
        this.badgeNumber = badgeNumber;
    }

    public int getActiveCaseCount() {
        return activeCaseCount;
    }

    public void setActiveCaseCount(int activeCaseCount) {
        this.activeCaseCount = activeCaseCount;
    }

    @Override
    public String toString() {
        return "Officer{" +
                "id='" + getId() + '\'' +
                ", name='" + getName() + '\'' +
                ", contact='" + getContact() + '\'' +
                ", badgeNumber='" + badgeNumber + '\'' +
                ", activeCaseCount=" + activeCaseCount +
                '}';
    }
}
