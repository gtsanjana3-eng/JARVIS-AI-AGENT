package com.jarvis.tools;

/**
 * The outcome of running a tool. {@code success} is decided by the tool's
 * code, never by the AI — so JARVIS can't report a failed action as done.
 *
 * @param attachment when not null, a document (e.g. a study timetable) shown to the user exactly
 *                   as produced, so the AI only needs to summarise it instead of re-typing it
 */
public record ToolResult(boolean success, String output, String attachment) {

    public static ToolResult ok(String output) {
        return new ToolResult(true, output, null);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, message, null);
    }

    /** Success whose document is shown to the user verbatim below JARVIS's reply. */
    public static ToolResult document(String summary, String document) {
        return new ToolResult(true, summary + "\n\n" + document, document);
    }

    /** Text sent back to the model as the observation. */
    public String forModel() {
        if (attachment != null) {
            return "SUCCESS: " + output + "\n\n(This document is already displayed to the user below your reply. "
                    + "Do not repeat it; summarise it in one or two sentences.)";
        }
        return (success ? "SUCCESS: " : "ERROR: ") + output;
    }
}
