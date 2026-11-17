package com.jarvis.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * The SQLite database file (data/jarvis.sqlite) and the low-level SQL helpers
 * every repository uses. All access goes through one connection guarded by
 * {@code synchronized}, which is the simplest correct model for SQLite.
 */
public class Database implements AutoCloseable {

    /** Turns one row of a result set into an object. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private static final String[] SCHEMA = {
        """
        CREATE TABLE IF NOT EXISTS meta (
            key   TEXT PRIMARY KEY,
            value TEXT)""",
        """
        CREATE TABLE IF NOT EXISTS conversations (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            title      TEXT    NOT NULL,
            updated_at INTEGER NOT NULL,
            created_at INTEGER NOT NULL)""",
        """
        CREATE TABLE IF NOT EXISTS messages (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
            role            TEXT    NOT NULL,
            content         TEXT    NOT NULL,
            run_id          INTEGER,
            created_at      INTEGER NOT NULL)""",
        "CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id, id)",
        """
        CREATE TABLE IF NOT EXISTS notes (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            title      TEXT,
            content    TEXT    NOT NULL,
            created_at INTEGER NOT NULL)""",
        """
        CREATE TABLE IF NOT EXISTS memories (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            content    TEXT    NOT NULL,
            category   TEXT    NOT NULL,
            created_at INTEGER NOT NULL)""",
        """
        CREATE TABLE IF NOT EXISTS reminders (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            text       TEXT    NOT NULL,
            due_at     INTEGER NOT NULL,
            status     TEXT    NOT NULL,
            fired_at   INTEGER,
            created_at INTEGER NOT NULL)""",
        """
        CREATE TABLE IF NOT EXISTS tasks (
            id           INTEGER PRIMARY KEY AUTOINCREMENT,
            title        TEXT    NOT NULL,
            priority     TEXT    NOT NULL,
            due_date     TEXT,
            status       TEXT    NOT NULL,
            completed_at INTEGER,
            created_at   INTEGER NOT NULL)""",
        """
        CREATE TABLE IF NOT EXISTS agent_runs (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            conversation_id INTEGER,
            request         TEXT    NOT NULL,
            intent          TEXT,
            plan            TEXT,
            status          TEXT    NOT NULL,
            result          TEXT,
            error           TEXT,
            finished_at     INTEGER,
            created_at      INTEGER NOT NULL)""",
        """
        CREATE TABLE IF NOT EXISTS agent_steps (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            run_id      INTEGER NOT NULL REFERENCES agent_runs(id) ON DELETE CASCADE,
            idx         INTEGER NOT NULL,
            kind        TEXT    NOT NULL,
            tool        TEXT,
            input       TEXT,
            output      TEXT,
            status      TEXT    NOT NULL,
            duration_ms INTEGER,
            created_at  INTEGER NOT NULL)""",
        "CREATE INDEX IF NOT EXISTS idx_steps_run ON agent_steps(run_id, idx)",
        """
        CREATE TABLE IF NOT EXISTS approvals (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            run_id     INTEGER NOT NULL REFERENCES agent_runs(id) ON DELETE CASCADE,
            tool       TEXT    NOT NULL,
            input      TEXT    NOT NULL,
            summary    TEXT    NOT NULL,
            risk       TEXT    NOT NULL,
            status     TEXT    NOT NULL,
            decided_at INTEGER,
            created_at INTEGER NOT NULL)""",
    };

    private final Path file;
    private final Connection conn;

    public Database(Path file) throws SQLException {
        this.file = file.toAbsolutePath();
        try {
            Files.createDirectories(this.file.getParent());
        } catch (java.io.IOException e) {
            throw new SQLException("Cannot create folder for database: " + this.file.getParent(), e);
        }
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + this.file);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000");
            for (String ddl : SCHEMA) {
                st.execute(ddl);
            }
        }
    }

    public synchronized long insert(String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Insert failed: " + e.getMessage(), e);
        }
    }

    public synchronized int update(String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Update failed: " + e.getMessage(), e);
        }
    }

    public synchronized <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Query failed: " + e.getMessage(), e);
        }
    }

    public long scalar(String sql, Object... params) {
        List<Long> r = query(sql, rs -> rs.getLong(1), params);
        return r.isEmpty() ? 0 : r.get(0);
    }

    public String getMeta(String key) {
        List<String> r = query("SELECT value FROM meta WHERE key = ?", rs -> rs.getString(1), key);
        return r.isEmpty() ? null : r.get(0);
    }

    public void setMeta(String key, String value) {
        update("INSERT INTO meta(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                key, value);
    }

    public Path getFile() {
        return file;
    }

    public long sizeBytes() {
        try {
            return Files.size(file);
        } catch (java.io.IOException e) {
            return 0;
        }
    }

    @Override
    public synchronized void close() {
        try {
            conn.close();
        } catch (SQLException ignored) {
            // shutting down
        }
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p == null) {
                ps.setNull(i + 1, java.sql.Types.NULL);
            } else if (p instanceof Enum<?> e) {
                ps.setString(i + 1, e.name());
            } else if (p instanceof Boolean b) {
                ps.setInt(i + 1, b ? 1 : 0);
            } else {
                ps.setObject(i + 1, p);
            }
        }
    }

    /** Unchecked wrapper so repository methods stay clean. */
    public static class DatabaseException extends RuntimeException {
        public DatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
