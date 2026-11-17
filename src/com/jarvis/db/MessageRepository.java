package com.jarvis.db;

import com.jarvis.model.Message;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Table "messages": every line of every conversation. */
public class MessageRepository extends SqliteRepository<Message> {

    public MessageRepository(Database db) {
        super(db, "messages", "conversation_id", "role", "content", "run_id");
    }

    @Override
    protected Object[] values(Message m) {
        return new Object[] { m.getConversationId(), m.getRole(), m.getText(), m.getRunId() };
    }

    @Override
    protected Message map(ResultSet rs) throws SQLException {
        return new Message(rs.getLong("id"), rs.getLong("conversation_id"), Message.Role.valueOf(rs.getString("role")),
                rs.getString("content"), nullableLong(rs, "run_id"), rs.getLong("created_at"));
    }

    public List<Message> forConversation(long conversationId) {
        return where("conversation_id = ?", conversationId);
    }

    /** The last {@code limit} messages of a conversation, oldest first — the agent's short-term context. */
    public List<Message> recent(long conversationId, int limit) {
        return latest(limit, "conversation_id = ?", conversationId);
    }

    public void deleteConversation(long conversationId) {
        db.update("DELETE FROM messages WHERE conversation_id = ?", conversationId);
    }
}
