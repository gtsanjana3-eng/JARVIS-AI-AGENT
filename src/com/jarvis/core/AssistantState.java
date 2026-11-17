package com.jarvis.core;

/**
 * Every state JARVIS can be in. The agent sets the reasoning states as it
 * really moves through them; the browser may only set the voice states
 * (LISTENING, SPEAKING) and IDLE.
 */
public enum AssistantState {
    IDLE,
    LISTENING,
    UNDERSTANDING,
    PLANNING,
    SELECTING_TOOLS,
    EXECUTING,
    AWAITING_APPROVAL,
    SPEAKING,
    COMPLETED,
    FAILED;

    /** States the browser is allowed to report (voice input/output). */
    public boolean isUserControllable() {
        return this == IDLE || this == LISTENING || this == SPEAKING;
    }
}
