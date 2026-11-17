package com.jarvis.server;

import com.jarvis.agent.Agent;
import com.jarvis.ai.LlmStatus;
import com.jarvis.core.AssistantState;
import com.jarvis.core.StateManager;
import com.jarvis.db.DataStore;
import com.jarvis.db.Repository;
import com.jarvis.model.AgentRun;
import com.jarvis.model.Note;
import com.jarvis.model.Reminder;
import com.jarvis.model.TaskItem;
import com.jarvis.system.SystemMonitor;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolParam;
import com.jarvis.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API used by the frontend. All responses are JSON.
 *
 * <pre>
 * GET    /api/status                    state, AI engine, CPU/memory, record counts
 * POST   /api/chat                      {"text", "conversationId"?} → starts an agent run (202)
 * POST   /api/agent/approve             {"approvalId", "approved"}
 * POST   /api/agent/cancel              stop the current run
 * GET    /api/agent/pending             the approval currently waiting, if any
 * POST   /api/state                     {"state": "LISTENING|SPEAKING|IDLE"} (voice states from the browser)
 * GET    /api/conversations             recent conversations
 * GET    /api/conversations/messages?id=N
 * DELETE /api/conversations?id=N        delete one conversation (omit id to delete all)
 * GET    /api/runs?limit=N              agent execution history with steps
 * GET    /api/tools                     tool registry
 * GET    /api/data/{notes|memories|reminders|tasks}
 * POST   /api/data/notes                {"content", "title"?}
 * POST   /api/data/tasks/complete?id=N
 * DELETE /api/data/{kind}?id=N
 * GET    /api/database                  table sizes
 * GET    /api/oop                       live OOP report (reflection on the running classes)
 * POST   /api/oop/{polymorphism|encapsulation}  live OOP demos
 * </pre>
 */
public class ApiHandler implements HttpHandler {

    private final Agent agent;
    private final DataStore store;
    private final StateManager state;
    private final SystemMonitor monitor;
    private final com.jarvis.oop.OopInspector oop;

    public ApiHandler(Agent agent, DataStore store, StateManager state, SystemMonitor monitor,
                      com.jarvis.oop.OopInspector oop) {
        this.oop = oop;
        this.agent = agent;
        this.store = store;
        this.state = state;
        this.monitor = monitor;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        try {
            if (path.startsWith("/api/data/")) {
                data(ex, method, path.substring("/api/data/".length()));
                return;
            }
            switch (method + " " + path) {
                case "GET /api/status" -> send(ex, 200, status());
                case "POST /api/chat" -> chat(ex, body(ex));
                case "POST /api/agent/approve" -> approve(ex, body(ex));
                case "POST /api/agent/cancel" -> send(ex, 200, Json.object().put("cancelled", agent.cancel()).toJson());
                case "GET /api/agent/pending" -> send(ex, 200, Json.stringify(agent.pendingApproval()));
                case "POST /api/state" -> send(ex, 200, setState(body(ex)));
                case "GET /api/conversations" -> send(ex, 200, Json.stringify(store.conversations.recent(50)));
                case "GET /api/conversations/messages" ->
                        send(ex, 200, Json.stringify(store.messages.forConversation(idParam(ex))));
                case "DELETE /api/conversations" -> deleteConversations(ex);
                case "GET /api/runs" -> send(ex, 200, runs(intParam(ex, "limit", 30)));
                case "GET /api/tools" -> send(ex, 200, toolList());
                case "GET /api/database" -> send(ex, 200, database());
                case "GET /api/oop" -> send(ex, 200, oop.overview());
                case "POST /api/oop/polymorphism" -> send(ex, 200, oop.polymorphismDemo());
                case "POST /api/oop/encapsulation" -> send(ex, 200, oop.encapsulationDemo());
                default -> send(ex, 404, error("Unknown endpoint " + method + " " + path));
            }
        } catch (IllegalArgumentException e) {
            send(ex, 400, error(e.getMessage()));
        } catch (IllegalStateException e) {
            send(ex, 409, error(e.getMessage()));
        } catch (RuntimeException e) {
            e.printStackTrace();
            send(ex, 500, error("Internal error: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ agent

    private void chat(HttpExchange ex, Map<String, Object> body) throws IOException {
        String text = requireString(body, "text");
        if (text.length() > 4000) {
            throw new IllegalArgumentException("Message is too long (max 4000 characters)");
        }
        Long conversationId = body.get("conversationId") instanceof Number n ? n.longValue() : null;
        try {
            Agent.Submission s = agent.submit(text, conversationId);
            send(ex, 202, Json.object()
                    .put("runId", s.runId())
                    .put("conversationId", s.conversationId())
                    .putValue("userMessage", s.userMessage())
                    .toJson());
        } catch (Agent.BusyException e) {
            send(ex, 409, error(e.getMessage()));
        }
    }

    private void approve(HttpExchange ex, Map<String, Object> body) throws IOException {
        if (!(body.get("approvalId") instanceof Number id) || !(body.get("approved") instanceof Boolean ok)) {
            throw new IllegalArgumentException("approvalId (number) and approved (true/false) are required");
        }
        agent.decide(id.longValue(), ok);
        send(ex, 200, Json.object().put("ok", true).toJson());
    }

    private String setState(Map<String, Object> body) {
        AssistantState next;
        try {
            next = AssistantState.valueOf(requireString(body, "state").toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown state");
        }
        if (!next.isUserControllable()) {
            throw new IllegalArgumentException(next + " is controlled by the agent, not the browser");
        }
        if (agent.isWorking()) {
            // The agent's reasoning state wins while it is busy.
            return Json.object().put("state", state.get().name()).put("ignored", true).toJson();
        }
        if (next == AssistantState.IDLE && agent.isAwaitingApproval()) {
            next = AssistantState.AWAITING_APPROVAL; // resting state while a decision is pending
            state.set(next, agent.pendingApproval().getSummary(), agent.activeRunId());
        } else {
            state.set(next, "", agent.activeRunId());
        }
        return Json.object().put("state", next.name()).toJson();
    }

    private String status() {
        LlmStatus llm = agent.llmStatus();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("conversations", store.conversations.count());
        counts.put("messages", store.messages.count());
        counts.put("notes", store.notes.count());
        counts.put("memories", store.memories.count());
        counts.put("reminders", store.reminders.pending().size());
        counts.put("tasks", store.tasks.byStatus(TaskItem.Status.OPEN).size());
        counts.put("runs", store.runs.count());
        return Json.object()
                .put("state", state.get().name())
                .put("stateDetail", state.getDetail())
                .putValue("llm", llm)
                .put("agentWorking", agent.isWorking())
                .put("awaitingApproval", agent.isAwaitingApproval())
                .put("activeRunId", agent.activeRunId())
                .put("tools", agent.tools().size())
                .putValue("counts", counts)
                .put("cpu", monitor.cpuPercent())
                .put("processCpu", monitor.processCpuPercent())
                .put("memory", monitor.memoryPercent())
                .put("memoryUsed", monitor.usedMemoryBytes())
                .put("memoryTotal", monitor.totalMemoryBytes())
                .put("jvmUsed", monitor.jvmUsedBytes())
                .put("jvmMax", monitor.jvmMaxBytes())
                .put("cores", monitor.processors())
                .put("os", monitor.osName())
                .put("java", monitor.javaVersion())
                .put("uptime", monitor.uptimeMillis())
                .put("serverTime", System.currentTimeMillis())
                .toJson();
    }

    private String runs(int limit) {
        List<String> out = new ArrayList<>();
        for (AgentRun r : store.runs.recent(Math.max(1, Math.min(100, limit)))) {
            out.add(Json.object()
                    .putRaw("run", r.toJson())
                    .putValue("steps", store.steps.forRun(r.getId()))
                    .toJson());
        }
        return Json.array(out);
    }

    private String toolList() {
        List<String> out = new ArrayList<>();
        for (Tool t : agent.tools().all()) {
            List<Map<String, Object>> params = new ArrayList<>();
            for (ToolParam p : t.getParams()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", p.name());
                m.put("type", p.type().name().toLowerCase());
                m.put("required", p.required());
                m.put("description", p.description());
                params.add(m);
            }
            out.add(Json.object()
                    .put("name", t.getName())
                    .put("category", t.getCategory())
                    .put("description", t.getDescription())
                    .put("risk", t.getRisk().name())
                    .put("requiresApproval", t.getRisk().requiresApproval())
                    .put("riskDescription", t.getRisk().description())
                    .put("className", t.getClass().getName().replace("com.jarvis.tools.builtin.", "").replace('$', '.'))
                    .putValue("params", params)
                    .toJson());
        }
        return Json.array(out);
    }

    private String database() {
        List<Map<String, Object>> tables = new ArrayList<>();
        for (Repository<?> r : store.all()) {
            tables.add(Map.of("name", r.tableName(), "rows", r.count()));
        }
        return Json.object()
                .put("engine", "SQLite (sqlite-jdbc)")
                .put("file", store.database().getFile().toString())
                .put("bytes", store.database().sizeBytes())
                .putValue("tables", tables)
                .toJson();
    }

    private void deleteConversations(HttpExchange ex) throws IOException {
        String q = ex.getRequestURI().getQuery();
        if (agent.isWorking()) {
            throw new IllegalStateException("Wait until JARVIS finishes the current request.");
        }
        if (q == null || !q.contains("id=")) {
            store.messages.deleteAll();
            store.conversations.deleteAll();
        } else {
            long id = idParam(ex);
            store.messages.deleteConversation(id);
            store.conversations.delete(id);
        }
        send(ex, 200, Json.object().put("ok", true).toJson());
    }

    // ------------------------------------------------------------------ user-managed data

    private void data(HttpExchange ex, String method, String kind) throws IOException {
        if (kind.equals("tasks/complete") && method.equals("POST")) {
            TaskItem t = store.tasks.findById(idParam(ex))
                    .orElseThrow(() -> new IllegalArgumentException("No such task"));
            t.complete();
            store.tasks.update(t);
            send(ex, 200, t.toJson());
            return;
        }
        Repository<?> repo = switch (kind) {
            case "notes" -> store.notes;
            case "memories" -> store.memories;
            case "reminders" -> store.reminders;
            case "tasks" -> store.tasks;
            default -> throw new IllegalArgumentException("Unknown data type: " + kind);
        };
        switch (method) {
            case "GET" -> {
                if (kind.equals("reminders")) {
                    List<Reminder> all = store.reminders.findAll();
                    send(ex, 200, Json.stringify(all.subList(Math.max(0, all.size() - 100), all.size())));
                } else {
                    send(ex, 200, Json.stringify(repo.findAll()));
                }
            }
            case "POST" -> {
                if (!kind.equals("notes")) {
                    throw new IllegalArgumentException("Only notes can be added directly; ask JARVIS for the rest");
                }
                Map<String, Object> body = body(ex);
                Object title = body.get("title");
                Note n = store.notes.save(new Note(title instanceof String s && !s.isBlank() ? s.trim() : null,
                        requireString(body, "content")));
                send(ex, 201, n.toJson());
            }
            case "DELETE" -> {
                boolean ok = repo.delete(idParam(ex));
                send(ex, ok ? 200 : 404, ok ? Json.object().put("ok", true).toJson() : error("Not found"));
            }
            default -> send(ex, 405, error("Method not allowed"));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> body(HttpExchange ex) throws IOException {
        String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return Json.parse(raw);
    }

    private static String requireString(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("Field '" + key + "' is required");
        }
        return s.trim();
    }

    private static String query(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) {
            return null;
        }
        for (String part : q.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv[0].equals(key) && kv.length == 2) {
                return kv[1];
            }
        }
        return null;
    }

    private static long idParam(HttpExchange ex) {
        try {
            return Long.parseLong(query(ex, "id"));
        } catch (NumberFormatException | NullPointerException e) {
            throw new IllegalArgumentException("A numeric 'id' query parameter is required");
        }
    }

    private static int intParam(HttpExchange ex, String key, int fallback) {
        try {
            String v = query(ex, key);
            return v == null ? fallback : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String error(String message) {
        return Json.object().put("error", message).toJson();
    }

    private static void send(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
