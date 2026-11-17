package com.jarvis.model;

import com.jarvis.util.Json;

/** One execution of the agent for one user request: its plan, status and outcome. */
public class AgentRun extends Entity {

    public enum Status { RUNNING, AWAITING_APPROVAL, COMPLETED, FAILED, CANCELLED, OFFLINE }

    private final Long conversationId;
    private final String request;
    private String intent;
    private String planJson;
    private Status status;
    private String result;
    private String error;
    private Long finishedAt;

    public AgentRun(long id, Long conversationId, String request, String intent, String planJson, Status status,
                    String result, String error, Long finishedAt, long createdAt) {
        super(id, createdAt);
        this.conversationId = conversationId;
        this.request = request;
        this.intent = intent;
        this.planJson = planJson;
        this.status = status;
        this.result = result;
        this.error = error;
        this.finishedAt = finishedAt;
    }

    public AgentRun(Long conversationId, String request) {
        this(0, conversationId, request, null, null, Status.RUNNING, null, null, null, System.currentTimeMillis());
    }

    public Long getConversationId() {
        return conversationId;
    }

    public String getRequest() {
        return request;
    }

    public String getIntent() {
        return intent;
    }

    public String getPlanJson() {
        return planJson;
    }

    public Status getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public String getError() {
        return error;
    }

    public Long getFinishedAt() {
        return finishedAt;
    }

    public void setPlan(String intent, String planJson) {
        this.intent = intent;
        this.planJson = planJson;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void finish(Status status, String result, String error) {
        this.status = status;
        this.result = result;
        this.error = error;
        this.finishedAt = System.currentTimeMillis();
    }

    @Override
    public String toJson() {
        return Json.object()
                .put("id", getId())
                .put("conversationId", conversationId)
                .put("request", request)
                .put("intent", intent)
                .putRaw("plan", planJson)
                .put("status", status.name())
                .put("result", result)
                .put("error", error)
                .put("finishedAt", finishedAt)
                .put("createdAt", getCreatedAt())
                .toJson();
    }
}
