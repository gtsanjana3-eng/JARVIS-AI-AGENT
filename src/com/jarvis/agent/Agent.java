package com.jarvis.agent;

import com.jarvis.agent.workflow.Workflow;
import com.jarvis.agent.workflow.WorkflowState;
import com.jarvis.ai.CancelToken;
import com.jarvis.ai.ChatMessage;
import com.jarvis.ai.LlmClient;
import com.jarvis.ai.LlmException;
import com.jarvis.ai.LlmReply;
import com.jarvis.ai.LlmStatus;
import com.jarvis.ai.ToolCall;
import com.jarvis.config.Config;
import com.jarvis.core.AssistantState;
import com.jarvis.core.EventBus;
import com.jarvis.core.StateManager;
import com.jarvis.db.DataStore;
import com.jarvis.model.AgentRun;
import com.jarvis.model.AgentStep;
import com.jarvis.model.Approval;
import com.jarvis.model.Conversation;
import com.jarvis.model.Memory;
import com.jarvis.model.Message;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolArgs;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolRegistry;
import com.jarvis.tools.ToolResult;
import com.jarvis.util.Json;

import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The autonomous agent.
 *
 * <pre>
 *  UNDERSTANDING  load conversation history + approved memories
 *  PLANNING       ask the model for a structured plan (answer / clarify / use tools)
 *  SELECTING_TOOLS choose the relevant tools from the registry
 *  EXECUTING      run each tool call, record the observation, let the model decide the next step
 *  AWAITING_APPROVAL  pause before any risky call until the user approves or denies
 *  COMPLETED / FAILED report the result honestly
 * </pre>
 *
 * Every step is saved to SQLite and broadcast to the UI as it happens.
 * Requests run one at a time on a background thread so the web server stays responsive.
 */
public class Agent {

    /** Returned to the browser immediately when a request is accepted. */
    public record Submission(long runId, long conversationId, Message userMessage) {
    }

    /** Thrown when a new request arrives while another is still running. */
    public static class BusyException extends Exception {
        public BusyException(String message) {
            super(message);
        }
    }

    private enum Phase { RUNNING, PAUSED, DONE }

    /** Working memory for one run. */
    private final class Session {
        final AgentRun run;
        final Conversation conversation;
        final String request;
        final CancelToken cancel = new CancelToken();
        final List<ChatMessage> llm = new ArrayList<>();
        final Deque<ToolCall> pending = new ArrayDeque<>();
        final List<String> problems = new ArrayList<>();
        final List<String> attachments = new ArrayList<>();
        List<Map<String, Object>> offeredTools = List.of();
        volatile Phase phase = Phase.RUNNING;
        int stepIndex;
        int modelTurns;
        int nudges;
        int toolSuccesses;
        boolean fastPath;
        boolean planPublished;
        Workflow workflow;
        final WorkflowState wfState = new WorkflowState();
        // when paused for approval
        Approval approval;
        AgentStep waitingStep;
        Tool waitingTool;
        ToolArgs waitingArgs;

        Session(AgentRun run, Conversation conversation, String request) {
            this.run = run;
            this.conversation = conversation;
            this.request = request;
        }
    }

    private final DataStore store;
    private final LlmClient llm;
    private final ToolRegistry tools;
    private final ToolContext toolContext;
    private final StateManager state;
    private final EventBus bus;
    private final Config config;
    private final OfflineEngine offline;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jarvis-agent");
        t.setDaemon(true);
        return t;
    });

    private volatile Session active;
    /** Tasks waiting for the user's answer to a question JARVIS asked, by conversation. */
    private final Map<Long, Workflow> waitingForAnswer = new java.util.concurrent.ConcurrentHashMap<>();

    public Agent(DataStore store, LlmClient llm, ToolRegistry tools, ToolContext toolContext, StateManager state,
                 EventBus bus, Config config, OfflineEngine offline) {
        this.store = store;
        this.llm = llm;
        this.tools = tools;
        this.toolContext = toolContext;
        this.state = state;
        this.bus = bus;
        this.config = config;
        this.offline = offline;
    }

    // ================================================================== public API

    /** Accepts a user message and starts processing it in the background. */
    public synchronized Submission submit(String text, Long conversationId) throws BusyException {
        Session current = active;
        if (current != null && current.phase == Phase.RUNNING) {
            throw new BusyException("I'm still working on your previous request. Wait a moment or press Stop.");
        }
        if (current != null && current.phase == Phase.PAUSED) {
            // A new message while an approval is pending: the old action is abandoned, not executed.
            expireApproval(current, "Superseded by a new request");
            finish(current, AgentRun.Status.CANCELLED, null,
                    "Okay, I've cancelled \"" + current.approval.getSummary() + "\" since you moved on.", false);
        }

        Conversation conv = conversationId == null ? null : store.conversations.findById(conversationId).orElse(null);
        if (conv == null) {
            conv = store.conversations.save(new Conversation(titleFrom(text)));
            bus.publish("conversation", Map.of("conversation", conv));
        }
        AgentRun run = store.runs.save(new AgentRun(conv.getId(), text));
        Message userMsg = store.messages.save(new Message(conv.getId(), Message.Role.USER, text, run.getId()));
        conv.touch();
        store.conversations.update(conv);

        Session s = new Session(run, conv, text);
        active = s;
        bus.publish("run_started", Map.of("run", run, "message", userMsg));
        worker.submit(() -> start(s));
        return new Submission(run.getId(), conv.getId(), userMsg);
    }

    /** The user's answer to a pending approval. */
    public synchronized void decide(long approvalId, boolean approved) {
        Session s = active;
        if (s == null || s.phase != Phase.PAUSED || s.approval == null || s.approval.getId() != approvalId) {
            throw new IllegalStateException("That approval is no longer pending.");
        }
        s.phase = Phase.RUNNING;
        worker.submit(() -> resume(s, approved));
    }

    /** Stops the current run (cancels a slow AI call, or an approval that is waiting). */
    public synchronized boolean cancel() {
        Session s = active;
        if (s == null || s.phase == Phase.DONE) {
            return false;
        }
        s.cancel.cancel();
        if (s.phase == Phase.PAUSED) {
            expireApproval(s, "Cancelled by the user");
            finish(s, AgentRun.Status.CANCELLED, null, "Stopped. I did not run \"" + s.approval.getSummary() + "\".", false);
        }
        return true;
    }

    public boolean isWorking() {
        Session s = active;
        return s != null && s.phase == Phase.RUNNING;
    }

    public boolean isAwaitingApproval() {
        Session s = active;
        return s != null && s.phase == Phase.PAUSED;
    }

    public Approval pendingApproval() {
        Session s = active;
        return s != null && s.phase == Phase.PAUSED ? s.approval : null;
    }

    public Long activeRunId() {
        Session s = active;
        return s != null && s.phase != Phase.DONE ? s.run.getId() : null;
    }

    public LlmStatus llmStatus() {
        return llm.status();
    }

    /**
     * Loads the model and pre-processes the (unchanging) instructions and tool list in the background,
     * so the user's first real question doesn't pay that cost.
     */
    public void warmUpAsync() {
        Thread t = new Thread(() -> {
            try {
                if (!llm.status().ready()) {
                    return;
                }
                long start = System.currentTimeMillis();
                llm.chat(List.of(ChatMessage.system(AgentPrompts.assistant(store.memories.findAll())),
                        ChatMessage.user("Reply with the single word: ready")), List.of(), null, null);
                System.out.println("  AI engine warmed up in " + (System.currentTimeMillis() - start) / 1000 + " s.");
            } catch (LlmException | RuntimeException e) {
                System.out.println("  AI warm-up skipped: " + e.getMessage());
            }
        }, "jarvis-warmup");
        t.setDaemon(true);
        t.start();
    }

    public ToolRegistry tools() {
        return tools;
    }

    // ================================================================== the loop

    private void start(Session s) {
        try {
            state.set(AssistantState.UNDERSTANDING, "Reading your request and the conversation", s.run.getId());
            Optional<FastRouter.Route> direct = FastRouter.route(s.request, toolContext.apps());

            // Was JARVIS waiting for an answer to a question it asked before starting a task?
            Workflow waiting = waitingForAnswer.remove(s.conversation.getId());
            if (waiting != null) {
                if (s.request.trim().matches("(?i)^(no|nope|cancel|stop|never ?mind|forget it|leave it|don'?t)\\W*$")) {
                    finish(s, AgentRun.Status.COMPLETED, "Okay, I've cancelled that.", null, true);
                    return;
                }
                boolean newTask = direct.isPresent() && direct.get().workflow() != null;
                if (!newTask) {
                    waiting.answer(s.request);
                    startWorkflow(s, waiting);
                    return;
                }
            }
            if (direct.isPresent()) {
                runFast(s, direct.get());
                return;
            }
            LlmStatus status = llm.status();
            if (!status.ready()) {
                runOffline(s, status);
                return;
            }
            // ---- SELECTING TOOLS (in Java): only what this request could need; none for plain questions.
            List<ChatMessage> history = history(s);
            String previousUser = null;
            for (ChatMessage m : history) {
                if (m.role().equals("user")) {
                    previousUser = m.content();
                }
            }
            List<Tool> selected = ToolSelector.select(s.request, previousUser, tools);
            s.offeredTools = selected.stream().map(Tool::schema).toList();
            List<String> names = selected.stream().map(Tool::getName).toList();
            state.set(AssistantState.SELECTING_TOOLS, selected.isEmpty() ? "No tools needed: answering from knowledge"
                    : "Selected " + selected.size() + " tool(s): " + String.join(", ", names), s.run.getId());
            if (!selected.isEmpty()) {
                bus.publish("tools_selected", Map.of("runId", s.run.getId(), "tools", names));
            }

            // ---- One reasoning pass: the model answers directly or uses the selected tools.
            // The system prompt never changes, so Ollama reuses its cached processing of it.
            state.set(AssistantState.PLANNING, selected.isEmpty() ? "Thinking" : "Deciding which steps to take",
                    s.run.getId());
            s.llm.add(ChatMessage.system(AgentPrompts.assistant(store.memories.findAll())));
            s.llm.addAll(history);
            s.llm.add(ChatMessage.user(s.request + "\n\n[" + AgentPrompts.clock(ZonedDateTime.now(toolContext.zone())) + "]"));
            loop(s);
        } catch (LlmException e) {
            handleLlmFailure(s, e);
        } catch (CancelledSignal e) {
            stopped(s);
        } catch (RuntimeException | Error e) { // never leave the agent stuck in "working"
            e.printStackTrace();
            fail(s, "Internal error: " + e.getMessage());
        }
    }

    private void stopped(Session s) {
        if (s.phase != Phase.DONE) {
            finish(s, AgentRun.Status.CANCELLED, "Stopped.", null, false);
        }
    }

    private void resume(Session s, boolean approved) {
        try {
            Approval a = s.approval;
            a.decide(approved ? Approval.Status.APPROVED : Approval.Status.DENIED);
            store.approvals.update(a);
            s.run.setStatus(AgentRun.Status.RUNNING);
            store.runs.update(s.run);
            bus.publish("approval_decided", Map.of("approval", a));
            if (approved) {
                execute(s, s.waitingStep, s.waitingTool, s.waitingArgs);
                s.approval = null;
                s.waitingStep = null;
                if (s.workflow != null) {
                    runWorkflow(s);
                    return;
                }
                if (s.fastPath) {
                    finishFast(s);
                    return;
                }
            } else if (s.workflow != null) {
                // Denied inside a workflow: record it; the workflow stops and reports what was and wasn't done.
                finishStep(s.waitingStep, AgentStep.Status.DENIED, "The user denied this action.");
                s.problems.add(s.waitingTool.getName() + " was denied");
                s.wfState.recordDenied(s.waitingTool.getName());
                s.pending.clear();
                s.approval = null;
                s.waitingStep = null;
                runWorkflow(s);
                return;
            } else {
                // The user said no: stop here with a fixed, accurate reply instead of letting the model guess why.
                finishStep(s.waitingStep, AgentStep.Status.DENIED, "The user denied this action.");
                s.problems.add(s.waitingTool.getName() + " was denied");
                s.pending.clear();
                String what = a.getSummary().isEmpty() ? "do that" : Character.toLowerCase(a.getSummary().charAt(0))
                        + a.getSummary().substring(1);
                s.approval = null;
                s.waitingStep = null;
                finish(s, AgentRun.Status.COMPLETED, "Okay, I won't " + what + ".", null, true);
                return;
            }
            s.approval = null;
            s.waitingStep = null;
            loop(s);
        } catch (LlmException e) {
            handleLlmFailure(s, e);
        } catch (CancelledSignal e) {
            stopped(s);
        } catch (RuntimeException | Error e) { // never leave the agent stuck in "working"
            e.printStackTrace();
            fail(s, "Internal error: " + e.getMessage());
        }
    }

    /** Reason → act → observe until the model gives a final answer, needs approval, or runs out of steps. */
    private void loop(Session s) throws LlmException {
        while (true) {
            checkCancelled(s);
            if (!processPending(s)) {
                return; // paused for approval
            }
            if (s.modelTurns >= config.agentMaxSteps()) {
                state.set(AssistantState.EXECUTING, "Step limit reached, summarising", s.run.getId());
                s.llm.add(ChatMessage.user(AgentPrompts.finalSummary()));
                LlmReply r = llm.chat(s.llm, List.of(), null, s.cancel);
                s.problems.add("stopped at the step limit");
                finish(s, AgentRun.Status.COMPLETED, r.content(), null, true);
                return;
            }
            state.set(AssistantState.SELECTING_TOOLS, s.modelTurns == 0 ? "Choosing the first action"
                    : "Reviewing results and choosing the next step", s.run.getId());
            LlmReply reply = llm.chat(s.llm, s.offeredTools, null, s.cancel);
            s.modelTurns++;
            List<ToolCall> calls = reply.hasToolCalls() ? reply.toolCalls() : inlineToolCall(reply.content());
            if (!s.planPublished) {
                publishModelPlan(s, calls);
            }
            if (calls.isEmpty()) {
                if (s.toolSuccesses == 0 && claimsAction(reply.content())) {
                    // Honesty rule: no tool succeeded, so nothing was done — never show a claim that it was.
                    AgentStep check = newStep(s, AgentStep.Kind.ERROR, null, null);
                    finishStep(check, AgentStep.Status.ERROR, "Blocked an unverified claim (no tool succeeded): \""
                            + reply.content() + "\"");
                    if (s.nudges < 2) {
                        s.nudges++;
                        s.llm.add(ChatMessage.assistant(reply.content()));
                        s.llm.add(ChatMessage.user("You have not used any tool, so nothing was actually done. Call the "
                                + "correct tool now, or tell me honestly that you cannot do it."));
                        continue;
                    }
                    finish(s, AgentRun.Status.COMPLETED, "I wasn't able to do that, so nothing has been done. "
                            + "Try a direct command such as \"open WhatsApp\" or \"play lofi music on YouTube\".", null, true);
                    return;
                }
                if (s.nudges < 2 && announcesMoreWork(reply.content())) {
                    // Small models sometimes describe their next action instead of performing it.
                    s.nudges++;
                    s.llm.add(ChatMessage.assistant(reply.content()));
                    s.llm.add(ChatMessage.user("Continue: do that now by calling the tool, instead of describing it."));
                    continue;
                }
                finish(s, AgentRun.Status.COMPLETED, reply.content(), null, true);
                return;
            }
            s.llm.add(ChatMessage.assistantToolCalls(reply.hasToolCalls() ? reply.content() : "", calls));
            s.pending.addAll(calls);
        }
    }

    /** Runs queued tool calls. Returns false if the run paused for approval. */
    private boolean processPending(Session s) {
        while (!s.pending.isEmpty()) {
            checkCancelled(s);
            ToolCall call = s.pending.poll();
            AgentStep step = newStep(s, AgentStep.Kind.TOOL, call.name(), Json.stringify(call.arguments()));
            Tool tool = tools.get(call.name()).orElse(null);
            if (tool == null) {
                observe(s, step, call.name(), ToolResult.error("Unknown tool '" + call.name()
                        + "'. Use only the tools provided."));
                continue;
            }
            ToolArgs args;
            try {
                args = tool.validate(call.arguments());
            } catch (ToolException e) {
                observe(s, step, tool.getName(), ToolResult.error(e.getMessage()));
                continue;
            }
            if (tool.requiresApproval(args, toolContext)) {
                Approval a = store.approvals.save(new Approval(s.run.getId(), tool.getName(), args.toJson(),
                        tool.describeCall(args), tool.riskFor(args, toolContext).name()));
                step.setStatus(AgentStep.Status.AWAITING_APPROVAL);
                store.steps.update(step);
                bus.publish("step", Map.of("step", step));
                s.approval = a;
                s.waitingStep = step;
                s.waitingTool = tool;
                s.waitingArgs = args;
                s.run.setStatus(AgentRun.Status.AWAITING_APPROVAL);
                store.runs.update(s.run);
                s.phase = Phase.PAUSED;
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("approval", a);
                e.put("riskDescription", tool.riskFor(args, toolContext).description());
                bus.publish("approval_required", e);
                state.set(AssistantState.AWAITING_APPROVAL, a.getSummary(), s.run.getId());
                return false;
            }
            execute(s, step, tool, args);
        }
        return true;
    }

    private void execute(Session s, AgentStep step, Tool tool, ToolArgs args) {
        state.set(AssistantState.EXECUTING, tool.describeCall(args), s.run.getId());
        step.setStatus(AgentStep.Status.RUNNING);
        bus.publish("step", Map.of("step", step));
        ToolResult result = tool.run(args, toolContext);
        observe(s, step, tool.getName(), result);
        if (result.success()) {
            bus.publish("data_changed", Map.of("category", tool.getCategory(), "tool", tool.getName()));
        }
    }

    /** Records a tool's outcome and feeds it back to the model as an observation. */
    private void observe(Session s, AgentStep step, String toolName, ToolResult result) {
        finishStep(step, result.success() ? AgentStep.Status.SUCCESS : AgentStep.Status.ERROR, result.output());
        if (result.success()) {
            s.toolSuccesses++;
        } else {
            s.problems.add(toolName + " failed");
        }
        if (result.attachment() != null) {
            s.attachments.add(result.attachment());
        }
        if (s.workflow != null) {
            s.wfState.record(toolName, result);
        }
        String observation = result.forModel();
        if (observation.length() > 9000) {
            observation = observation.substring(0, 9000) + "… [truncated]";
        }
        s.llm.add(ChatMessage.toolResult(toolName, observation));
    }

    /** Records what the model decided on its first turn as the plan for this request. */
    private void publishModelPlan(Session s, List<ToolCall> calls) {
        s.planPublished = true;
        List<String> steps = new ArrayList<>();
        for (ToolCall c : calls) {
            String desc = c.name();
            Tool t = tools.get(c.name()).orElse(null);
            if (t != null) {
                try {
                    desc = t.describeCall(t.validate(c.arguments()));
                } catch (ToolException ignored) {
                    // invalid input: shown by name, the step itself reports the error
                }
            }
            steps.add(desc);
        }
        if (!calls.isEmpty()) {
            steps.add("Review the results and report back");
        }
        Plan plan = calls.isEmpty()
                ? new Plan("answer from knowledge", Plan.Mode.ANSWER, "", List.of(), List.of())
                : new Plan("act with tools", Plan.Mode.USE_TOOLS, "", steps, calls.stream().map(ToolCall::name).toList());
        s.run.setPlan(plan.intent(), plan.toJson());
        store.runs.update(s.run);
        AgentStep planStep = newStep(s, AgentStep.Kind.PLAN, null, null);
        finishStep(planStep, AgentStep.Status.SUCCESS, plan.toJson());
        bus.publish("plan", Map.of("runId", s.run.getId(), "plan", plan));
        if (!calls.isEmpty()) {
            bus.publish("tools_selected", Map.of("runId", s.run.getId(), "tools", plan.tools()));
        }
    }

    // ================================================================== workflows

    /** Starts a multi-step workflow: shows its plan, then runs it step by step. */
    private void startWorkflow(Session s, Workflow wf) {
        String question = wf.question();
        if (question != null) {
            // Ask first instead of acting blindly; the user's next message is the answer.
            waitingForAnswer.put(s.conversation.getId(), wf);
            Plan ask = new Plan(wf.intent(), Plan.Mode.CLARIFY, question, List.of("Ask before starting: " + question),
                    List.of());
            s.run.setPlan(ask.intent(), ask.toJson());
            store.runs.update(s.run);
            AgentStep planStep = newStep(s, AgentStep.Kind.PLAN, null, null);
            finishStep(planStep, AgentStep.Status.SUCCESS, ask.toJson());
            bus.publish("plan", Map.of("runId", s.run.getId(), "plan", ask));
            finish(s, AgentRun.Status.COMPLETED, question, null, true);
            return;
        }
        s.workflow = wf;
        Plan plan = new Plan(wf.intent(), Plan.Mode.USE_TOOLS, "", wf.plannedSteps(), List.of());
        s.run.setPlan(plan.intent(), plan.toJson());
        store.runs.update(s.run);
        AgentStep planStep = newStep(s, AgentStep.Kind.PLAN, null, null);
        finishStep(planStep, AgentStep.Status.SUCCESS, plan.toJson());
        bus.publish("plan", Map.of("runId", s.run.getId(), "plan", plan));
        runWorkflow(s);
    }

    /** Runs workflow steps until it finishes or pauses for approval. Each step goes through the normal tool path. */
    private void runWorkflow(Session s) {
        while (true) {
            checkCancelled(s);
            ToolCall next = s.workflow.next(s.wfState);
            if (next == null) {
                finish(s, AgentRun.Status.COMPLETED, s.workflow.summary(s.wfState), null, true);
                return;
            }
            s.pending.add(next);
            if (!processPending(s)) {
                return; // paused for approval
            }
        }
    }

    // ================================================================== fast path

    /** Runs a clearly recognised command straight through its tool — no language-model call needed. */
    private void runFast(Session s, FastRouter.Route route) {
        if (route.workflow() != null) {
            startWorkflow(s, route.workflow());
            return;
        }
        Plan plan = new Plan(route.intent(), Plan.Mode.USE_TOOLS, "",
                List.of("Recognised a direct command (fast path, no AI call needed)", "Run " + route.call().name()),
                List.of(route.call().name()));
        s.run.setPlan(plan.intent(), plan.toJson());
        store.runs.update(s.run);
        AgentStep planStep = newStep(s, AgentStep.Kind.PLAN, null, null);
        finishStep(planStep, AgentStep.Status.SUCCESS, plan.toJson());
        bus.publish("plan", Map.of("runId", s.run.getId(), "plan", plan));
        bus.publish("tools_selected", Map.of("runId", s.run.getId(), "tools", List.of(route.call().name())));
        s.fastPath = true;
        s.pending.add(route.call());
        if (processPending(s)) {
            finishFast(s);
        }
    }

    /** Reports the fast-path tool's real outcome in plain words. */
    private void finishFast(Session s) {
        AgentStep last = null;
        for (AgentStep st : store.steps.forRun(s.run.getId())) {
            if (st.getKind() == AgentStep.Kind.TOOL) {
                last = st;
            }
        }
        if (last == null || last.getOutput() == null) {
            finish(s, AgentRun.Status.COMPLETED, "Nothing was done.", null, true);
            return;
        }
        String out = last.getOutput().trim();
        String reply;
        if (last.getStatus() != AgentStep.Status.SUCCESS) {
            reply = "I couldn't do that: " + out;
        } else if ("get_datetime".equals(last.getTool())) {
            ZonedDateTime now = ZonedDateTime.now(toolContext.zone());
            reply = "It's " + com.jarvis.util.SpokenTime.time(now.toLocalTime()) + ", "
                    + now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM")) + ".";
        } else if ("recall_memories".equals(last.getTool()) && out.startsWith("#")) {
            List<String> facts = out.lines().map(l -> l.replaceFirst("^#\\d+ \\([a-z]+\\) ", "").trim()).toList();
            reply = "Here's what I remember about you: " + String.join("; ", facts) + ".";
        } else if ("calculator".equals(last.getTool()) && out.contains(" = ")) {
            reply = "That's " + out.substring(out.lastIndexOf(" = ") + 3) + ".";
        } else {
            reply = out;
        }
        finish(s, AgentRun.Status.COMPLETED, reply, null, true);
    }

    // ================================================================== offline mode

    private void runOffline(Session s, LlmStatus status) {
        AgentStep step = newStep(s, AgentStep.Kind.OFFLINE, null, null);
        OfflineEngine.Result r = offline.handle(s.request);
        String reply;
        if (r != null) {
            reply = r.reply();
            finishStep(step, AgentStep.Status.SUCCESS, "Offline mode: handled by rule-based skill '" + r.skillName()
                    + "' because the AI engine is unavailable (" + status.detail() + ").");
        } else {
            reply = "My AI core is offline (" + status.detail() + "), so right now I can only handle simple "
                    + "commands like the time, the date, calculations and notes. Start Ollama and ask me again.";
            finishStep(step, AgentStep.Status.ERROR, "Offline mode: no rule-based skill matched. " + status.detail());
        }
        finish(s, AgentRun.Status.OFFLINE, reply, null, true);
    }

    // ================================================================== completion

    private void finish(Session s, AgentRun.Status status, String text, String error, boolean speak) {
        String reply = text == null || text.isBlank() ? (error != null ? error : summariseSteps(s)) : text.trim();
        for (String doc : s.attachments) {
            reply += "\n\n" + doc.trim(); // exact tool output, not re-typed by the model
        }
        Message msg = store.messages.save(new Message(s.conversation.getId(), Message.Role.JARVIS, reply,
                s.run.getId()));
        s.run.finish(status, reply, status == AgentRun.Status.FAILED ? error : null);
        store.runs.update(s.run);
        s.conversation.touch();
        store.conversations.update(s.conversation);
        s.phase = Phase.DONE;

        Map<String, Object> e = new LinkedHashMap<>();
        e.put("run", s.run);
        e.put("message", msg);
        e.put("problems", s.problems);
        e.put("speak", speak);
        bus.publish("run_finished", e);
        AssistantState end = switch (status) {
            case FAILED -> AssistantState.FAILED;
            case CANCELLED -> AssistantState.IDLE;
            default -> AssistantState.COMPLETED;
        };
        state.set(end, status == AgentRun.Status.OFFLINE ? "Answered in offline mode"
                : s.problems.isEmpty() ? "" : String.join("; ", s.problems), s.run.getId());
    }

    private void fail(Session s, String message) {
        AgentStep step = newStep(s, AgentStep.Kind.ERROR, null, null);
        finishStep(step, AgentStep.Status.ERROR, message);
        finish(s, AgentRun.Status.FAILED, null, "I couldn't complete that. " + message, true);
    }

    private void handleLlmFailure(Session s, LlmException e) {
        if (e.getKind() == LlmException.Kind.CANCELLED || s.cancel.isCancelled()) {
            stopped(s);
            return;
        }
        fail(s, e.getMessage());
    }

    private void checkCancelled(Session s) {
        if (s.cancel.isCancelled()) {
            throw new CancelledSignal();
        }
    }

    /** Unchecked signal used to unwind the loop when the user presses Stop. */
    private static final class CancelledSignal extends RuntimeException {
        CancelledSignal() {
            super("cancelled", null, false, false);
        }
    }

    private void expireApproval(Session s, String why) {
        if (s.approval != null && s.approval.getStatus() == Approval.Status.PENDING) {
            s.approval.decide(Approval.Status.EXPIRED);
            store.approvals.update(s.approval);
            if (s.waitingStep != null) {
                finishStep(s.waitingStep, AgentStep.Status.DENIED, why);
            }
            bus.publish("approval_decided", Map.of("approval", s.approval));
        }
    }

    // ================================================================== helpers

    private List<ChatMessage> history(Session s) {
        List<Message> recent = store.messages.recent(s.conversation.getId(), config.agentHistoryMessages() + 1);
        List<ChatMessage> out = new ArrayList<>();
        for (Message m : recent) {
            if (m.getRunId() != null && m.getRunId() == s.run.getId()) {
                continue; // the current request is added separately
            }
            String text = m.getText().length() > 1500 ? m.getText().substring(0, 1500) + "…" : m.getText();
            out.add(m.getRole() == Message.Role.USER ? ChatMessage.user(text) : ChatMessage.assistant(text));
        }
        return out;
    }

    private AgentStep newStep(Session s, AgentStep.Kind kind, String tool, String input) {
        AgentStep step = store.steps.save(new AgentStep(s.run.getId(), ++s.stepIndex, kind, tool, input));
        bus.publish("step", Map.of("step", step));
        return step;
    }

    private void finishStep(AgentStep step, AgentStep.Status status, String output) {
        step.finish(status, output);
        store.steps.update(step);
        bus.publish("step", Map.of("step", step));
    }

    /** Fallback for small models that write a tool call as JSON text instead of using the tool-call format. */
    private List<ToolCall> inlineToolCall(String content) {
        if (content == null) {
            return List.of();
        }
        String t = content.trim().replaceAll("^```(json)?|```$", "").trim();
        if (!t.startsWith("{") || !t.contains("\"name\"")) {
            return List.of();
        }
        try {
            Map<String, Object> m = Json.parse(t);
            String name = Json.asString(m.get("name"), "");
            Object args = m.containsKey("arguments") ? m.get("arguments") : m.get("parameters");
            if (tools.get(name).isPresent()) {
                return List.of(new ToolCall(name, Json.asMap(args)));
            }
        } catch (IllegalArgumentException ignored) {
            // not a tool call after all
        }
        return List.of();
    }

    /** True when a reply says an action was performed ("Chrome has been opened", "I've set a reminder"). */
    private static boolean claimsAction(String content) {
        if (content == null) {
            return false;
        }
        // First-person or "has been <done>" phrasing only, so facts like "Java was created in 1995" don't trigger it.
        return content.matches("(?is).*(\\b(has been|have been|i've|i have|i just|i successfully)\\s+(\\w+\\s+){0,3}"
                + "(opened|started|launched|saved|created|set|scheduled|added|sent|deleted|stored|written|moved|"
                + "played|organi[sz]ed|noted|booked)\\b|\\bis now (open|running|playing|saved|set)\\b"
                + "|(?:^|[.!]\\s+)(stored|saved|opened|started|launched|created|added|sent|deleted|scheduled|noted|set up)\\s+"
                + "(?:your|the|a|an|it|that|this)\\b).*");
    }

    /** True when a reply promises further action ("Let me search again...") rather than finishing. */
    private static boolean announcesMoreWork(String content) {
        if (content == null || content.isBlank() || content.trim().endsWith("?")) {
            return false;
        }
        return content.matches("(?is).*\\b(let me|i will|i'll|i am going to|i'm going to|next,? i will)\\s+(now\\s+)?"
                + "(search|look|check|find|refine|try|read|get|fetch|open|create|calculate|write|save|list|research)\\b.*");
    }

    private String summariseSteps(Session s) {
        List<AgentStep> steps = store.steps.forRun(s.run.getId());
        StringBuilder sb = new StringBuilder();
        for (AgentStep st : steps) {
            if (st.getKind() == AgentStep.Kind.TOOL && st.getOutput() != null) {
                sb.append(st.getStatus() == AgentStep.Status.SUCCESS ? "Done: " : "Not done: ")
                        .append(st.getOutput().lines().findFirst().orElse("")).append('\n');
            }
        }
        return sb.isEmpty() ? "Done." : sb.toString().trim();
    }

    private static String titleFrom(String text) {
        String t = text.trim().replaceAll("\\s+", " ");
        return t.length() > 48 ? t.substring(0, 47) + "…" : t;
    }
}
