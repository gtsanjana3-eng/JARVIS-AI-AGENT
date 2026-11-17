package com.jarvis.db;

import com.jarvis.model.Entity;

import java.util.List;
import java.util.Optional;

/**
 * Storage contract for any kind of entity.
 *
 * OOP: INTERFACE / ABSTRACTION. The rest of the application only talks to this
 * interface, so the storage engine can change (it moved from flat files to
 * SQLite) without touching the assistant logic.
 */
public interface Repository<T extends Entity> {

    T save(T entity);

    void update(T entity);

    List<T> findAll();

    Optional<T> findById(long id);

    boolean delete(long id);

    void deleteAll();

    int count();

    /** Name of the table, e.g. "messages". */
    String tableName();
}
