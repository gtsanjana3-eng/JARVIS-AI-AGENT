package com.jarvis.tools;

import java.util.List;

/** Declares one input of a tool: its name, type, description, and whether it is required. */
public record ToolParam(String name, Type type, String description, boolean required, List<String> allowed) {

    public enum Type { STRING, INTEGER, NUMBER, BOOLEAN }

    public static ToolParam required(String name, Type type, String description) {
        return new ToolParam(name, type, description, true, List.of());
    }

    public static ToolParam optional(String name, Type type, String description) {
        return new ToolParam(name, type, description, false, List.of());
    }

    /** A string limited to fixed values. */
    public static ToolParam choice(String name, boolean required, String description, String... values) {
        return new ToolParam(name, Type.STRING, description, required, List.of(values));
    }

    String jsonType() {
        return switch (type) {
            case STRING -> "string";
            case INTEGER -> "integer";
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
        };
    }
}
