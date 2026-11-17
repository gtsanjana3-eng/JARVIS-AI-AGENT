package com.jarvis.db;

import com.jarvis.model.Conversation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Table "conversations". */
public class ConversationRepository extends SqliteRepository<Conversation> {

    public ConversationRepository(Database db) {
        super(db, "conversations", "title", "updated_at");
    }

    @Override
    protected Object[] values(Conversation c) {
        return new Object[] { c.getTitle(), c.getUpdatedAt() };
    }

    @Override
    protected Conversation map(ResultSet rs) throws SQLException {
        return new Conversation(rs.getLong("id"), rs.getString("title"), rs.getLong("updated_at"),
                rs.getLong("created_at"));
    }

    /** Most recently active first. */
    public List<Conversation> recent(int limit) {
        return db.query("SELECT * FROM conversations ORDER BY updated_at DESC LIMIT ?", this::map, limit);
    }
}
