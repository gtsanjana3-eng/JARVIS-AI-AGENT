package com.jarvis.model;

import com.jarvis.util.Json;

/** One recorded step inside an {@link AgentRun}: a plan, a tool call, or a reasoning error. */
public class AgentStep extends Entity {

    public enum Kind { PLAN, TOOL, ERROR, OFFLINE }

    public enum Status { RUNNING, SUCCESS, ERROR, AWAITING_APPROVAL, DENIED }

    private final long runId;
    private final int index;
    private final Kind kind;
    private final String tool;
    private final String input;
    private String output;
    private Status status;
    private Long durationMs;

    public AgentStep(long id, long runId, int index, Kind kind, String tool, String input, String output,
                     Status status, Long durationMs, long createdAt) {
        super(id, createdAt);
        this.runId = runId;
        this.index = index;
        this.kind = kind;
        this.tool = tool;
        this.input = input;
        this.output = output;
        this.status = status;
        this.durationMs = durationMs;
    }

    public AgentStep(long runId, int index, Kind kind, String tool, String input) {
        this(0, runId, index, kind, tool, input, null, Status.RUNNING, null, System.currentTimeMillis());
    }

    public long getRunId() {
        return runId;
    }

    public int getIndex() {
        return index;
    }

    public Kind getKind() {
        return kind;
    }

    public String getTool() {
        return tool;
    }

    public String getInput() {
        return input;
    }

    public String getOutput() {
        return output;
    }

    public Status getStatus() {
        return status;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void finish(Status status, String output) {
        this.status = status;
        this.output = output;
        this.durationMs = System.currentTimeMillis() - getCreatedAt();
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public String toJson() {
        return Json.object()
                .put("id", getId())
                .put("runId", runId)
                .put("index", index)
                .put("kind", kind.name())
                .put("tool", tool)
                .put("input", input)
                .put("output", output)
                .put("status", status.name())
                .put("durationMs", durationMs)
                .put("createdAt", getCreatedAt())
                .toJson();
    }
}
