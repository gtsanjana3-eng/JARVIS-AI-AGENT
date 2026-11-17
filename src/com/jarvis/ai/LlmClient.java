package com.jarvis.ai;

import java.util.List;
import java.util.Map;

/**
 * Anything that can act as JARVIS's reasoning engine.
 *
 * OOP: INTERFACE. The agent depends only on this contract, so a different
 * provider (another local engine or a cloud API) can be added as a new class
 * without changing the agent.
 */
public interface LlmClient {

    /** Human-readable provider name, e.g. "Ollama". */
    String providerName();

    /** Model in use, e.g. "qwen2.5:3b". */
    String model();

    /**
     * Sends a conversation and returns the model's reply.
     *
     * @param messages   conversation so far (system, user, assistant and tool messages)
     * @param tools      tool definitions the model may call (may be empty)
     * @param jsonSchema when not null, the reply text must be JSON matching this schema
     * @param cancel     lets the caller abort a slow request
     */
    LlmReply chat(List<ChatMessage> messages, List<Map<String, Object>> tools, Map<String, Object> jsonSchema,
                  CancelToken cancel) throws LlmException;

    /** Quick health check, safe to call often. */
    LlmStatus status();
}
