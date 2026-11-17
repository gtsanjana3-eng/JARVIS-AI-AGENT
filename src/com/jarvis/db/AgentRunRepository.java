package com.jarvis.db;

import com.jarvis.model.AgentRun;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Table "agent_runs": the execution history of the agent. */
public class AgentRunRepository extends SqliteRepository<AgentRun> {

    public AgentRunRepository(Database db) {
        super(db, "agent_runs", "conversation_id", "request", "intent", "plan", "status", "result", "error",
                "finished_at");
    }

    @Override
    protected Object[] values(AgentRun r) {
        return new Object[] { r.getConversationId(), r.getRequest(), r.getIntent(), r.getPlanJson(), r.getStatus(),
                r.getResult(), r.getError(), r.getFinishedAt() };
    }

    @Override
    protected AgentRun map(ResultSet rs) throws SQLException {
        return new AgentRun(rs.getLong("id"), nullableLong(rs, "conversation_id"), rs.getString("request"),
                rs.getString("intent"), rs.getString("plan"), AgentRun.Status.valueOf(rs.getString("status")),
                rs.getString("result"), rs.getString("error"), nullableLong(rs, "finished_at"),
                rs.getLong("created_at"));
    }

    /** Newest first. */
    public List<AgentRun> recent(int limit) {
        return db.query("SELECT * FROM agent_runs ORDER BY id DESC LIMIT ?", this::map, limit);
    }

    /** Runs left unfinished when the app was closed can never resume — mark them honestly. */
    public int failInterrupted() {
        return db.update("UPDATE agent_runs SET status = 'FAILED', error = 'Interrupted: JARVIS was restarted', "
                + "finished_at = ? WHERE status IN ('RUNNING', 'AWAITING_APPROVAL')", System.currentTimeMillis());
    }
}
