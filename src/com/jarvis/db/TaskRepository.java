package com.jarvis.db;

import com.jarvis.model.TaskItem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Table "tasks": the user's to-do list. */
public class TaskRepository extends SqliteRepository<TaskItem> {

    public TaskRepository(Database db) {
        super(db, "tasks", "title", "priority", "due_date", "status", "completed_at");
    }

    @Override
    protected Object[] values(TaskItem t) {
        return new Object[] { t.getTitle(), t.getPriority(), t.getDueDate(), t.getStatus(), t.getCompletedAt() };
    }

    @Override
    protected TaskItem map(ResultSet rs) throws SQLException {
        return new TaskItem(rs.getLong("id"), rs.getString("title"),
                TaskItem.Priority.valueOf(rs.getString("priority")), rs.getString("due_date"),
                TaskItem.Status.valueOf(rs.getString("status")), nullableLong(rs, "completed_at"),
                rs.getLong("created_at"));
    }

    public List<TaskItem> byStatus(TaskItem.Status status) {
        return where("status = ?", status);
    }
}
