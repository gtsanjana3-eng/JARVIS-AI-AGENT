package com.jarvis.db;

import com.jarvis.model.Entity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Generic SQLite implementation of {@link Repository}.
 *
 * OOP: ABSTRACT CLASS + TEMPLATE METHOD + GENERICS. All SQL for insert,
 * update, select and delete is written once here. Each subclass only says
 * which columns it has ({@link #values}) and how to rebuild an object from a
 * row ({@link #map}).
 */
public abstract class SqliteRepository<T extends Entity> implements Repository<T> {

    protected final Database db;
    private final String table;
    private final String[] columns; // excluding "id" and "created_at"

    protected SqliteRepository(Database db, String table, String... columns) {
        this.db = db;
        this.table = table;
        this.columns = columns;
    }

    /** Column values for {@code entity}, in the same order as the constructor's column names. */
    protected abstract Object[] values(T entity);

    /** Rebuild an entity from the current row. */
    protected abstract T map(ResultSet rs) throws SQLException;

    @Override
    public T save(T entity) {
        String cols = String.join(", ", columns) + ", created_at";
        String marks = "?, ".repeat(columns.length) + "?";
        Object[] params = Arrays.copyOf(values(entity), columns.length + 1);
        params[columns.length] = entity.getCreatedAt();
        long id = db.insert("INSERT INTO " + table + " (" + cols + ") VALUES (" + marks + ")", params);
        entity.assignId(id);
        return entity;
    }

    @Override
    public void update(T entity) {
        String set = String.join(" = ?, ", columns) + " = ?";
        Object[] params = Arrays.copyOf(values(entity), columns.length + 1);
        params[columns.length] = entity.getId();
        db.update("UPDATE " + table + " SET " + set + " WHERE id = ?", params);
    }

    @Override
    public List<T> findAll() {
        return db.query("SELECT * FROM " + table + " ORDER BY id", this::map);
    }

    @Override
    public Optional<T> findById(long id) {
        return where("id = ?", id).stream().findFirst();
    }

    @Override
    public boolean delete(long id) {
        return db.update("DELETE FROM " + table + " WHERE id = ?", id) > 0;
    }

    @Override
    public void deleteAll() {
        db.update("DELETE FROM " + table);
    }

    @Override
    public int count() {
        return (int) db.scalar("SELECT COUNT(*) FROM " + table);
    }

    @Override
    public String tableName() {
        return table;
    }

    /** Rows matching a WHERE clause, oldest first. */
    protected List<T> where(String clause, Object... params) {
        return db.query("SELECT * FROM " + table + " WHERE " + clause + " ORDER BY id", this::map, params);
    }

    /** The newest {@code limit} rows matching a clause, returned oldest first. */
    protected List<T> latest(int limit, String clause, Object... params) {
        Object[] p = Arrays.copyOf(params, params.length + 1);
        p[params.length] = limit;
        return db.query("SELECT * FROM (SELECT * FROM " + table + " WHERE " + clause
                + " ORDER BY id DESC LIMIT ?) ORDER BY id", this::map, p);
    }

    /** Reads a nullable INTEGER column. */
    protected static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }
}
