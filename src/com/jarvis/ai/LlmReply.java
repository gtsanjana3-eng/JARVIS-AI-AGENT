package com.jarvis.ai;

import java.util.List;

/** What the model answered: text, tool calls, or both. */
public record LlmReply(String content, List<ToolCall> toolCalls, long durationMs) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
