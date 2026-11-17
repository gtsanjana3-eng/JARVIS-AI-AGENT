package com.jarvis.model;

import com.jarvis.util.Json;

/** A note the user (or JARVIS on the user's request) saved. */
public class Note extends Entity {

    private final String title;
    private final String content;

    public Note(long id, String title, String content, long createdAt) {
        super(id, createdAt);
        this.title = title;
        this.content = content;
    }

    public Note(String title, String content) {
        this(0, title, content, System.currentTimeMillis());
    }

    public Note(String content) {
        this(null, content);
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toJson() {
        return Json.object()
                .put("id", getId())
                .put("title", title)
                .put("content", content)
                .put("createdAt", getCreatedAt())
                .toJson();
    }
}
