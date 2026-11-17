package com.jarvis.model;

import com.jarvis.util.Json;

/** A chat thread. Messages and agent runs belong to a conversation. */
public class Conversation extends Entity {

    private String title;
    private long updatedAt;

    public Conversation(long id, String title, long updatedAt, long createdAt) {
        super(id, createdAt);
        this.title = title;
        this.updatedAt = updatedAt;
    }

    public Conversation(String title) {
        this(0, title, System.currentTimeMillis(), System.currentTimeMillis());
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    @Override
    public String toJson() {
        return Json.object()
                .put("id", getId())
                .put("title", title)
                .put("updatedAt", updatedAt)
                .put("createdAt", getCreatedAt())
                .toJson();
    }
}
