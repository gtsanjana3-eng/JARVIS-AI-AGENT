package com.jarvis.db;

import com.jarvis.model.Approval;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Table "approvals": risky actions and the user's decisions about them. */
public class ApprovalRepository extends SqliteRepository<Approval> {

    public ApprovalRepository(Database db) {
        super(db, "approvals", "run_id", "tool", "input", "summary", "risk", "status", "decided_at");
    }

    @Override
    protected Object[] values(Approval a) {
        return new Object[] { a.getRunId(), a.getTool(), a.getInputJson(), a.getSummary(), a.getRisk(),
                a.getStatus(), a.getDecidedAt() };
    }

    @Override
    protected Approval map(ResultSet rs) throws SQLException {
        return new Approval(rs.getLong("id"), rs.getLong("run_id"), rs.getString("tool"), rs.getString("input"),
                rs.getString("summary"), rs.getString("risk"), Approval.Status.valueOf(rs.getString("status")),
                nullableLong(rs, "decided_at"), rs.getLong("created_at"));
    }

    public List<Approval> pending() {
        return where("status = 'PENDING'");
    }

    public int expireAllPending() {
        return db.update("UPDATE approvals SET status = 'EXPIRED', decided_at = ? WHERE status = 'PENDING'",
                System.currentTimeMillis());
    }
}
