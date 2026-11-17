package com.jarvis.model;

import com.jarvis.util.Json;

/**
 * Base class for every record stored in the JARVIS database.
 *
 * OOP: ABSTRACTION + INHERITANCE + ENCAPSULATION.
 * Common fields (id, createdAt) live here once; subclasses such as
 * {@link Message}, {@link Note} or {@link AgentRun} inherit them and only add
 * their own data. Fields are private and exposed through getters only.
 */
public abstract class Entity implements Json.JsonSerializable {

    private long id;
    private final long createdAt;

    protected Entity(long id, long createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    /** Called by the repository when the record is first saved and receives its auto-increment id. */
    public void assignId(long id) {
        if (this.id != 0) {
            throw new IllegalStateException("Entity already has an id: " + this.id);
        }
        this.id = id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /** Every entity must be able to describe itself as JSON for the frontend (polymorphism). */
    @Override
    public abstract String toJson();
}
