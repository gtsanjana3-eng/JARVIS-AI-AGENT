package com.jarvis.model;

import com.jarvis.util.Json;

/** A risky tool call that is waiting for (or received) the user's decision. */
public class Approval extends Entity {

    public enum Status { PENDING, APPROVED, DENIED, EXPIRED }

    private final long runId;
    private final String tool;
    private final String inputJson;
    private final String summary;
    private final String risk;
    private Status status;
    private Long decidedAt;

    public Approval(long id, long runId, String tool, String inputJson, String summary, String risk, Status status,
                    Long decidedAt, long createdAt) {
        super(id, createdAt);
        this.runId = runId;
        this.tool = tool;
        this.inputJson = inputJson;
        this.summary = summary;
        this.risk = risk;
        this.status = status;
        this.decidedAt = decidedAt;
    }

    public Approval(long runId, String tool, String inputJson, String summary, String risk) {
        this(0, runId, tool, inputJson, summary, risk, Status.PENDING, null, System.currentTimeMillis());
    }

    public long getRunId() {
        return runId;
    }

    public String getTool() {
        return tool;
    }

    public String getInputJson() {
        return inputJson;
    }

    public String getSummary() {
        return summary;
    }

    public String getRisk() {
        return risk;
    }

    public Status getStatus() {
        return status;
    }

    public Long getDecidedAt() {
        return decidedAt;
    }

    public void decide(Status status) {
        this.status = status;
        this.decidedAt = System.currentTimeMillis();
    }

    @Override
    public String toJson() {
        return Json.object()
                .put("id", getId())
                .put("runId", runId)
                .put("tool", tool)
                .putRaw("input", inputJson)
                .put("summary", summary)
                .put("risk", risk)
                .put("status", status.name())
                .put("decidedAt", decidedAt)
                .put("createdAt", getCreatedAt())
                .toJson();
    }
}
