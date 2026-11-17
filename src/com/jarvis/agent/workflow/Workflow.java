package com.jarvis.agent.workflow;

import com.jarvis.ai.ToolCall;

import java.util.List;

/**
 * A multi-step task the agent knows how to carry out end-to-end, e.g.
 * "research a topic and put it in Word": search → read → write document →
 * open it in the requested app → report back.
 *
 * OOP: ABSTRACT CLASS + POLYMORPHISM. The agent runs any workflow the same
 * way: it repeatedly asks {@link #next} for the next tool call, runs that call
 * through the normal tool system (validation, approval, recording), and feeds
 * the result back via {@link WorkflowState}. Each subclass decides its own
 * steps, adapting to earlier results and stopping honestly when a step fails.
 */
public abstract class Workflow {

    private final String intent;

    protected Workflow(String intent) {
        this.intent = intent;
    }

    public String intent() {
        return intent;
    }

    /**
     * A question to ask the user before starting, when something important is missing (where to put the
     * result, how detailed, who a letter is for...). Null when the workflow has everything it needs.
     */
    public String question() {
        return null;
    }

    /** Takes the user's reply to {@link #question()}. Missing details fall back to sensible defaults. */
    public void answer(String reply) {
        // default: nothing to fill in
    }

    /** The plan shown to the user before work starts. */
    public abstract List<String> plannedSteps();

    /** The next tool call, or null when the workflow is finished (or must stop). */
    public abstract ToolCall next(WorkflowState state);

    /** The final, spoken answer — based only on what the tools actually returned. */
    public abstract String summary(WorkflowState state);
}
