package com.jarvis.ai;

import java.util.List;

/**
 * One message in the conversation sent to the model.
 *
 * @param role      "system", "user", "assistant" or "tool"
 * @param content   message text
 * @param toolCalls tool calls made by the assistant in this message (assistant only)
 * @param toolName  which tool produced this result (tool messages only)
 */
public record ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolName) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, List.of(), null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, List.of(), null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, List.of(), null);
    }

    public static ChatMessage assistantToolCalls(String content, List<ToolCall> calls) {
        return new ChatMessage("assistant", content == null ? "" : content, calls, null);
    }

    public static ChatMessage toolResult(String toolName, String content) {
        return new ChatMessage("tool", content, List.of(), toolName);
    }
}
