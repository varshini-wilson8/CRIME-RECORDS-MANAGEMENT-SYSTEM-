package model;

/**
 * Abstract base class for any human entity in the system
 * (e.g. Officer, Suspect). Demonstrates ABSTRACTION: it defines
 * common state and behaviour but cannot be instantiated directly,
 * and forces subclasses to define their own role.
 */
public abstract class Person {

    // ENCAPSULATION: fields are private, accessed only via getters/setters
    private String id;
    private String name;
    private String contact;

    public Person(String id, String name, String contact) {
        this.id = id;
        this.name = name;
        this.contact = contact;
    }

    /**
     * Abstract method - every subclass must define what "role"
     * that type of person plays in the system (e.g. "Officer", "Suspect").
     */
    public abstract String getRole();

    // ----- Getters and Setters -----

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    @Override
    public String toString() {
        return "Person{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", contact='" + contact + '\'' +
                ", role='" + getRole() + '\'' +
                '}';
    }
}
