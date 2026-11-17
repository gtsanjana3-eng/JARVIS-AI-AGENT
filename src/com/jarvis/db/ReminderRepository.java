package com.jarvis.db;

import com.jarvis.model.Reminder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Table "reminders". */
public class ReminderRepository extends SqliteRepository<Reminder> {

    public ReminderRepository(Database db) {
        super(db, "reminders", "text", "due_at", "status", "fired_at");
    }

    @Override
    protected Object[] values(Reminder r) {
        return new Object[] { r.getText(), r.getDueAt(), r.getStatus(), r.getFiredAt() };
    }

    @Override
    protected Reminder map(ResultSet rs) throws SQLException {
        return new Reminder(rs.getLong("id"), rs.getString("text"), rs.getLong("due_at"),
                Reminder.Status.valueOf(rs.getString("status")), nullableLong(rs, "fired_at"),
                rs.getLong("created_at"));
    }

    public List<Reminder> pending() {
        return db.query("SELECT * FROM reminders WHERE status = 'PENDING' ORDER BY due_at", this::map);
    }

    public List<Reminder> dueBy(long time) {
        return db.query("SELECT * FROM reminders WHERE status = 'PENDING' AND due_at <= ? ORDER BY due_at",
                this::map, time);
    }
}
