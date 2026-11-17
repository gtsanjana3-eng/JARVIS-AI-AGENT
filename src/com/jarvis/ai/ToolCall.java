package com.jarvis.ai;

import java.util.Map;

/** A request from the model to run one tool with the given arguments. */
public record ToolCall(String name, Map<String, Object> arguments) {
}
