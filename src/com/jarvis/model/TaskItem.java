package com.jarvis.model;

import com.jarvis.util.Json;

/** A to-do item. */
public class TaskItem extends Entity {

    public enum Status { OPEN, DONE }

    public enum Priority { LOW, MEDIUM, HIGH }

    private final String title;
    private final Priority priority;
    private final String dueDate;
    private Status status;
    private Long completedAt;

    public TaskItem(long id, String title, Priority priority, String dueDate, Status status, Long completedAt,
                    long createdAt) {
        super(id, createdAt);
        this.title = title;
        this.priority = priority;
        this.dueDate = dueDate;
        this.status = status;
        this.completedAt = completedAt;
    }

    public TaskItem(String title, Priority priority, String dueDate) {
        this(0, title, priority, dueDate, Status.OPEN, null, System.currentTimeMillis());
    }

    public String getTitle() {
        return title;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getDueDate() {
        return dueDate;
    }

    public Status getStatus() {
        return status;
    }

    public Long getCompletedAt() {
        return completedAt;
    }

    public void complete() {
        status = Status.DONE;
        completedAt = System.currentTimeMillis();
    }

    @Override
    public String toJson() {
        return Json.object()
                .put("id", getId())
                .put("title", title)
                .put("priority", priority.name())
                .put("dueDate", dueDate)
                .put("status", status.name())
                .put("completedAt", completedAt)
                .put("createdAt", getCreatedAt())
                .toJson();
    }
}
