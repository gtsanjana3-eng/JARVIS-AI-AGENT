package com.jarvis.tools;

import com.jarvis.util.Json;

import java.util.Collections;
import java.util.Map;

/** Validated, typed arguments for one tool call (created by {@link Tool#validate}). */
public final class ToolArgs {

    private final Map<String, Object> values;

    ToolArgs(Map<String, Object> values) {
        this.values = values;
    }

    public boolean has(String name) {
        return values.get(name) != null;
    }

    public String string(String name) {
        return (String) values.get(name);
    }

    public String string(String name, String fallback) {
        return has(name) ? string(name) : fallback;
    }

    public long integer(String name) {
        return (Long) values.get(name);
    }

    public long integer(String name, long fallback) {
        return has(name) ? integer(name) : fallback;
    }

    public double number(String name, double fallback) {
        return has(name) ? (Double) values.get(name) : fallback;
    }

    public boolean bool(String name, boolean fallback) {
        return has(name) ? (Boolean) values.get(name) : fallback;
    }

    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(values);
    }

    public String toJson() {
        return Json.stringify(values);
    }
}
