package com.jarvis.db;

import com.jarvis.model.AgentStep;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Table "agent_steps": every plan, tool call and error inside a run. */
public class AgentStepRepository extends SqliteRepository<AgentStep> {

    public AgentStepRepository(Database db) {
        super(db, "agent_steps", "run_id", "idx", "kind", "tool", "input", "output", "status", "duration_ms");
    }

    @Override
    protected Object[] values(AgentStep s) {
        return new Object[] { s.getRunId(), s.getIndex(), s.getKind(), s.getTool(), s.getInput(), s.getOutput(),
                s.getStatus(), s.getDurationMs() };
    }

    @Override
    protected AgentStep map(ResultSet rs) throws SQLException {
        return new AgentStep(rs.getLong("id"), rs.getLong("run_id"), rs.getInt("idx"),
                AgentStep.Kind.valueOf(rs.getString("kind")), rs.getString("tool"), rs.getString("input"),
                rs.getString("output"), AgentStep.Status.valueOf(rs.getString("status")),
                nullableLong(rs, "duration_ms"), rs.getLong("created_at"));
    }

    public List<AgentStep> forRun(long runId) {
        return where("run_id = ?", runId);
    }
}
