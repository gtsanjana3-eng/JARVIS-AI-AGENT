package com.jarvis.db;

import com.jarvis.model.Memory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Table "memories": long-term facts the user approved. */
public class MemoryRepository extends SqliteRepository<Memory> {

    public MemoryRepository(Database db) {
        super(db, "memories", "content", "category");
    }

    @Override
    protected Object[] values(Memory m) {
        return new Object[] { m.getContent(), m.getCategory() };
    }

    @Override
    protected Memory map(ResultSet rs) throws SQLException {
        return new Memory(rs.getLong("id"), rs.getString("content"), rs.getString("category"),
                rs.getLong("created_at"));
    }

    public List<Memory> search(String text) {
        String like = "%" + text.toLowerCase() + "%";
        return where("lower(content) LIKE ? OR lower(category) LIKE ?", like, like);
    }
}
