package com.jarvis.model;

import com.jarvis.util.Json;

/** One line of conversation between the user and JARVIS. */
public class Message extends Entity {

    public enum Role { USER, JARVIS }

    private final long conversationId;
    private final Role role;
    private final String text;
    private final Long runId;

    public Message(long id, long conversationId, Role role, String text, Long runId, long createdAt) {
        super(id, createdAt);
        this.conversationId = conversationId;
        this.role = role;
        this.text = text;
        this.runId = runId;
    }

    /** A brand-new (unsaved) message. */
    public Message(long conversationId, Role role, String text, Long runId) {
        this(0, conversationId, role, text, runId, System.currentTimeMillis());
    }

    public long getConversationId() {
        return conversationId;
    }

    public Role getRole() {
        return role;
    }

    public String getText() {
        return text;
    }

    public Long getRunId() {
        return runId;
    }

    @Override
    public String toJson() {
        return Json.object()
                .put("id", getId())
                .put("conversationId", conversationId)
                .put("role", role.name())
                .put("text", text)
                .put("runId", runId)
                .put("createdAt", getCreatedAt())
                .toJson();
    }
}
