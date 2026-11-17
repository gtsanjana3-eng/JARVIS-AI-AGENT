package com.jarvis.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Settings from config/jarvis.properties. The file is created with sensible
 * defaults on first start so it is easy to find and edit (e.g. to change the
 * AI model). Nothing secret is ever sent to the browser.
 */
public class Config {

    private static final String DEFAULTS = """
            # ==========================================================
            #  J.A.R.V.I.S. configuration
            #  Edit this file, then restart run.bat.
            # ==========================================================

            # Web interface port
            server.port=8080

            # Local AI engine (Ollama, free, runs on this PC)
            #   Install Ollama from https://ollama.com, then in a terminal:  ollama pull qwen2.5:3b
            #   For better answers on a faster PC try:  qwen2.5:7b
            ollama.url=http://localhost:11434
            ollama.model=qwen2.5:3b
            # Seconds to wait for one AI response (CPU-only PCs can be slow)
            ollama.timeoutSeconds=300
            # Context window in tokens (higher = more memory use)
            ollama.contextTokens=8192

            # Agent behaviour
            agent.maxSteps=8
            agent.historyMessages=12

            # The only folder JARVIS may read, write or organise files in
            workspace.dir=workspace
            """;

    private final Properties props = new Properties();
    private final Path file;

    public Config(Path file) throws IOException {
        this.file = file.toAbsolutePath();
        if (!Files.exists(this.file)) {
            Files.createDirectories(this.file.getParent());
            Files.writeString(this.file, DEFAULTS, StandardCharsets.UTF_8); // search settings are appended below
        }
        // Older config files are upgraded in place with any newer settings (existing values are kept).
        if (!Files.readString(this.file, StandardCharsets.UTF_8).contains("search.provider")) {
            Files.writeString(this.file, Files.readString(this.file, StandardCharsets.UTF_8) + "\n" + SEARCH_SETTINGS,
                    StandardCharsets.UTF_8);
        }
        try (Reader r = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            props.load(r);
        }
    }

    private static final String SEARCH_SETTINGS = """
            # ----------------------------------------------------------
            #  Web search for accurate answers
            #  search.provider = wikipedia (default, no key needed), google or brave
            #
            #  Google (official Programmable Search API):
            #    1. https://programmablesearchengine.google.com -> Add -> "Search the entire web" -> copy the Search engine ID
            #    2. https://console.cloud.google.com -> APIs & Services -> enable "Custom Search API" -> Credentials -> API key
            #    3. Paste both below and set search.provider=google, then restart run.bat
            #  Brave Search: https://brave.com/search/api -> get an API key -> paste below, search.provider=brave
            # ----------------------------------------------------------
            search.provider=wikipedia
            google.apiKey=
            google.cx=
            brave.apiKey=
            """;

    public String searchProvider() {
        return get("search.provider", "wikipedia");
    }

    public String get(String key, String fallback) {
        String v = props.getProperty(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public Path getFile() {
        return file;
    }

    public int port() {
        return getInt("server.port", 8080);
    }

    public String ollamaUrl() {
        String url = get("ollama.url", "http://localhost:11434");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String ollamaModel() {
        return get("ollama.model", "qwen2.5:3b");
    }

    public int ollamaTimeoutSeconds() {
        return getInt("ollama.timeoutSeconds", 300);
    }

    public int ollamaContextTokens() {
        return getInt("ollama.contextTokens", 8192);
    }

    public int agentMaxSteps() {
        return getInt("agent.maxSteps", 8);
    }

    public int agentHistoryMessages() {
        return getInt("agent.historyMessages", 12);
    }

    public Path workspaceDir() {
        return Path.of(get("workspace.dir", "workspace")).toAbsolutePath().normalize();
    }
}
