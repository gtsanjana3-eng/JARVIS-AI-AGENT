package com.jarvis.agent;

import com.jarvis.util.Json;

import java.util.List;

/**
 * The plan for one request, shown in the Activity panel and stored with the run.
 * It comes from a workflow ({@code plannedSteps}), the fast command router, or the
 * model's first decision.
 *
 * @param intent what the user wants, in a few words
 * @param mode   ANSWER (reply directly), CLARIFY (ask a question first) or USE_TOOLS
 * @param reply  a clarifying question (CLARIFY only)
 * @param steps  the steps that will be carried out
 * @param tools  tool names involved
 */
public record Plan(String intent, Mode mode, String reply, List<String> steps, List<String> tools)
        implements Json.JsonSerializable {

    public enum Mode { ANSWER, CLARIFY, USE_TOOLS }

    @Override
    public String toJson() {
        return Json.object()
                .put("intent", intent)
                .put("mode", mode.name())
                .put("reply", reply)
                .putValue("steps", steps)
                .putValue("tools", tools)
                .toJson();
    }
}
