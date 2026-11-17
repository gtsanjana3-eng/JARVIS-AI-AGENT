package com.jarvis.model;

import com.jarvis.util.Json;

/** Something JARVIS must alert the user about at a specific time. */
public class Reminder extends Entity {

    public enum Status { PENDING, FIRED, CANCELLED }

    private final String text;
    private final long dueAt;
    private Status status;
    private Long firedAt;

    public Reminder(long id, String text, long dueAt, Status status, Long firedAt, long createdAt) {
        super(id, createdAt);
        this.text = text;
        this.dueAt = dueAt;
        this.status = status;
        this.firedAt = firedAt;
    }

    public Reminder(String text, long dueAt) {
        this(0, text, dueAt, Status.PENDING, null, System.currentTimeMillis());
    }

    public String getText() {
        return text;
    }

    public long getDueAt() {
        return dueAt;
    }

    public Status getStatus() {
        return status;
    }

    public Long getFiredAt() {
        return firedAt;
    }

    public void markFired() {
        status = Status.FIRED;
        firedAt = System.currentTimeMillis();
    }

    public void cancel() {
        status = Status.CANCELLED;
    }

    @Override
    public String toJson() {
        return Json.object()
                .put("id", getId())
                .put("text", text)
                .put("dueAt", dueAt)
                .put("status", status.name())
                .put("firedAt", firedAt)
                .put("createdAt", getCreatedAt())
                .toJson();
    }
}
