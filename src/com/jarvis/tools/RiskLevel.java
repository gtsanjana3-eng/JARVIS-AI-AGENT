package com.jarvis.tools;

/** How dangerous a tool call is, which decides whether the user must approve it first. */
public enum RiskLevel {
    SAFE(false, "Safe: reads or adds your own data"),
    NETWORK_READ(false, "Reads public information from the internet"),
    SENSITIVE(true, "Stores personal data or changes existing data"),
    EXTERNAL(true, "Opens programs or websites on this computer"),
    DESTRUCTIVE(true, "Deletes, overwrites or moves data");

    private final boolean requiresApproval;
    private final String description;

    RiskLevel(boolean requiresApproval, String description) {
        this.requiresApproval = requiresApproval;
        this.description = description;
    }

    public boolean requiresApproval() {
        return requiresApproval;
    }

    public String description() {
        return description;
    }
}
