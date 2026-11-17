package com.jarvis.model;

import com.jarvis.util.Json;

/**
 * A long-term fact about the user that the user explicitly approved JARVIS
 * to remember (e.g. "Exam on 12 Nov: Maths, Physics").
 */
public class Memory extends Entity {

    private final String content;
    private final String category;

    public Memory(long id, String content, String category, long createdAt) {
        super(id, createdAt);
        this.content = content;
        this.category = category;
    }

    public Memory(String content, String category) {
        this(0, content, category, System.currentTimeMillis());
    }

    public String getContent() {
        return content;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public String toJson() {
        return Json.object()
                .put("id", getId())
                .put("content", content)
                .put("category", category)
                .put("createdAt", getCreatedAt())
                .toJson();
    }
}
