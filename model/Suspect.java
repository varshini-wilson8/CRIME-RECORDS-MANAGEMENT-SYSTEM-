package model;

/**
 * Represents a suspect who may be linked to one or more crime cases.
 * Demonstrates INHERITANCE (extends Person) and POLYMORPHISM
 * (overrides getRole() and toString()).
 */
public class Suspect extends Person {

    private String physicalDescription;
    private String riskLevel;      // e.g. "LOW", "MEDIUM", "HIGH"
    private boolean repeatOffender; // set by the repeat-offender auto-flag feature

    public Suspect(String id, String name, String contact,
                    String physicalDescription, String riskLevel) {
        super(id, name, contact);
        this.physicalDescription = physicalDescription;
        this.riskLevel = riskLevel;
        this.repeatOffender = false;
    }

    @Override
    public String getRole() {
        return "Suspect";
    }

    // ----- Getters and Setters -----

    public String getPhysicalDescription() {
        return physicalDescription;
    }

    public void setPhysicalDescription(String physicalDescription) {
        this.physicalDescription = physicalDescription;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public boolean isRepeatOffender() {
        return repeatOffender;
    }

    public void setRepeatOffender(boolean repeatOffender) {
        this.repeatOffender = repeatOffender;
    }

    @Override
    public String toString() {
        return "Suspect{" +
                "id='" + getId() + '\'' +
                ", name='" + getName() + '\'' +
                ", contact='" + getContact() + '\'' +
                ", physicalDescription='" + physicalDescription + '\'' +
                ", riskLevel='" + riskLevel + '\'' +
                ", repeatOffender=" + repeatOffender +
                '}';
    }
}
