package com.jarvis.db;

import com.jarvis.model.Note;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Table "notes". */
public class NoteRepository extends SqliteRepository<Note> {

    public NoteRepository(Database db) {
        super(db, "notes", "title", "content");
    }

    @Override
    protected Object[] values(Note n) {
        return new Object[] { n.getTitle(), n.getContent() };
    }

    @Override
    protected Note map(ResultSet rs) throws SQLException {
        return new Note(rs.getLong("id"), rs.getString("title"), rs.getString("content"), rs.getLong("created_at"));
    }

    /** Case-insensitive search in title and content. */
    public List<Note> search(String text) {
        String like = "%" + text.toLowerCase() + "%";
        return where("lower(content) LIKE ? OR lower(coalesce(title, '')) LIKE ?", like, like);
    }
}
