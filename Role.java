public enum Role {
    ADMIN("Administrator"),
    COMMAND_OFFICER("Command Officer"),
    FIELD_AGENT("Field Agent");

    private final String title;

    Role(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public boolean canAccess(String module) {
        if (module == null) return false;
        switch (this) {
            case ADMIN:
            case COMMAND_OFFICER:
                return true;
            case FIELD_AGENT:
                return !module.equalsIgnoreCase("SUSPECTS") &&
                       !module.equalsIgnoreCase("OFFICERS") &&
                       !module.equalsIgnoreCase("COMMAND_CENTER");
            default:
                return false;
        }
    }
}
