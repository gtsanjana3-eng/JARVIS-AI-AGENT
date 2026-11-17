package com.jarvis.ai;

import com.jarvis.util.Json;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Talks to a local Ollama server (https://ollama.com) through its REST API
 * using only Java's built-in {@link HttpClient}. Free, offline, no API key.
 */
public class OllamaClient implements LlmClient {

    private static final long STATUS_CACHE_MS = 4000;

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final int contextTokens;
    private final HttpClient http;

    private LlmStatus cachedStatus;
    private long cachedAt;

    public OllamaClient(String baseUrl, String model, int timeoutSeconds, int contextTokens) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.contextTokens = contextTokens;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    public String providerName() {
        return "Ollama";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public LlmReply chat(List<ChatMessage> messages, List<Map<String, Object>> tools, Map<String, Object> jsonSchema,
                         CancelToken cancel) throws LlmException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages.stream().map(OllamaClient::toWire).toList());
        body.put("stream", false);
        body.put("keep_alive", "60m"); // keep the model in memory between requests
        // Cap output length: a structured plan is short, and on a CPU every extra token costs time.
        body.put("options", Map.of("temperature", 0.2, "num_ctx", contextTokens,
                "num_predict", jsonSchema != null ? 160 : 700));
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }
        if (jsonSchema != null) {
            body.put("format", jsonSchema);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body)))
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> response = send(request, cancel);
        long ms = System.currentTimeMillis() - start;

        Map<String, Object> json;
        try {
            json = Json.parse(response.body());
        } catch (IllegalArgumentException e) {
            throw new LlmException(LlmException.Kind.BAD_RESPONSE, "The AI engine returned an unreadable response.");
        }
        if (response.statusCode() != 200) {
            String err = Json.asString(json.get("error"), "HTTP " + response.statusCode());
            if (response.statusCode() == 404 || err.contains("not found")) {
                throw new LlmException(LlmException.Kind.MODEL_MISSING, "The AI model '" + model
                        + "' is not installed. Open a terminal and run:  ollama pull " + model);
            }
            throw new LlmException(LlmException.Kind.HTTP_ERROR, "The AI engine reported an error: " + err);
        }

        Map<String, Object> msg = Json.asMap(json.get("message"));
        String content = stripThinking(Json.asString(msg.get("content"), ""));
        List<ToolCall> calls = new ArrayList<>();
        for (Object o : Json.asList(msg.get("tool_calls"))) {
            Map<String, Object> fn = Json.asMap(Json.asMap(o).get("function"));
            Object args = fn.get("arguments");
            if (args instanceof String s && !s.isBlank()) {
                try {
                    args = Json.parseAny(s);
                } catch (IllegalArgumentException ignored) {
                    args = Map.of();
                }
            }
            calls.add(new ToolCall(Json.asString(fn.get("name"), ""), Json.asMap(args)));
        }
        return new LlmReply(content, calls, ms);
    }

    private HttpResponse<String> send(HttpRequest request, CancelToken cancel) throws LlmException {
        CompletableFuture<HttpResponse<String>> future = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        if (cancel != null) {
            cancel.track(future);
        }
        try {
            return future.get();
        } catch (CancellationException | InterruptedException e) {
            throw new LlmException(LlmException.Kind.CANCELLED, "Stopped by the user.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cancel != null && cancel.isCancelled()) {
                throw new LlmException(LlmException.Kind.CANCELLED, "Stopped by the user.");
            }
            if (cause instanceof HttpTimeoutException) {
                throw new LlmException(LlmException.Kind.TIMEOUT, "The AI engine took longer than "
                        + timeout.toSeconds() + " seconds to answer. Try a shorter request or a smaller model.");
            }
            if (cause instanceof ConnectException || (cause instanceof IOException
                    && String.valueOf(cause.getMessage()).toLowerCase().contains("connect"))) {
                throw new LlmException(LlmException.Kind.NOT_RUNNING, "I can't reach Ollama at " + baseUrl
                        + ". Please start the Ollama app and try again.", cause);
            }
            throw new LlmException(LlmException.Kind.HTTP_ERROR, "Network error talking to the AI engine: "
                    + cause.getMessage(), cause);
        }
    }

    @Override
    public synchronized LlmStatus status() {
        long now = System.currentTimeMillis();
        if (cachedStatus != null && now - cachedAt < STATUS_CACHE_MS) {
            return cachedStatus;
        }
        LlmStatus s;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            List<String> names = new ArrayList<>();
            for (Object m : Json.asList(Json.parse(res.body()).get("models"))) {
                names.add(Json.asString(Json.asMap(m).get("name"), ""));
            }
            boolean installed = names.contains(model) || names.contains(model + ":latest");
            s = new LlmStatus(providerName(), model, true, installed,
                    installed ? "Ready" : "Model not installed — run: ollama pull " + model, names);
        } catch (IOException | RuntimeException e) {
            s = new LlmStatus(providerName(), model, false, false,
                    "Ollama is not running at " + baseUrl, List.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            s = new LlmStatus(providerName(), model, false, false, "Interrupted", List.of());
        }
        cachedStatus = s;
        cachedAt = now;
        return s;
    }

    /** Ollama wire format for one message. */
    private static Map<String, Object> toWire(ChatMessage m) {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("role", m.role());
        w.put("content", m.content() == null ? "" : m.content());
        if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            w.put("tool_calls", m.toolCalls().stream()
                    .map(c -> Map.of("function", Map.of("name", c.name(), "arguments", c.arguments())))
                    .toList());
        }
        if (m.toolName() != null) {
            w.put("tool_name", m.toolName());
        }
        return w;
    }

    /** Some models wrap private reasoning in &lt;think&gt; tags; the user should only see the answer. */
    private static String stripThinking(String s) {
        return s.replaceAll("(?s)<think>.*?</think>", "").trim();
    }
}
