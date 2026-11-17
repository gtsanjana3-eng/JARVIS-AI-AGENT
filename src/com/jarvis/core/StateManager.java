package com.jarvis.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for the assistant's current state. Every change is
 * broadcast immediately, so the UI shows what the agent is really doing.
 */
public class StateManager {

    private final EventBus bus;
    private AssistantState state = AssistantState.IDLE;
    private String detail = "";
    private long changedAt = System.currentTimeMillis();

    public StateManager(EventBus bus) {
        this.bus = bus;
    }

    public synchronized AssistantState get() {
        return state;
    }

    public synchronized String getDetail() {
        return detail;
    }

    public synchronized long getChangedAt() {
        return changedAt;
    }

    public void set(AssistantState next) {
        set(next, "", null);
    }

    public void set(AssistantState next, String detail, Long runId) {
        synchronized (this) {
            state = next;
            this.detail = detail == null ? "" : detail;
            changedAt = System.currentTimeMillis();
        }
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("state", next.name());
        e.put("detail", detail == null ? "" : detail);
        e.put("runId", runId);
        bus.publish("state", e);
    }
}
