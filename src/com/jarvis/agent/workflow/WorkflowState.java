package com.jarvis.agent.workflow;

import com.jarvis.tools.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The results of the steps a workflow has run so far. */
public final class WorkflowState {

    /** One finished step. */
    public record Step(String tool, ToolResult result, boolean denied) {
    }

    private static final Pattern PATH = Pattern.compile("PATH=(.+?)\\s*$", Pattern.MULTILINE);

    private final List<Step> steps = new ArrayList<>();

    public void record(String tool, ToolResult result) {
        steps.add(new Step(tool, result, false));
    }

    public void recordDenied(String tool) {
        steps.add(new Step(tool, ToolResult.error("The user did not approve this step."), true));
    }

    public int count() {
        return steps.size();
    }

    public Step last() {
        return steps.isEmpty() ? null : steps.get(steps.size() - 1);
    }

    /** True if the last step failed or was denied — workflows stop rather than guess. */
    public boolean stopped() {
        Step l = last();
        return l != null && (l.denied() || !l.result().success());
    }

    /** The successful result of a tool, or null. */
    public ToolResult resultOf(String tool) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            Step s = steps.get(i);
            if (s.tool().equals(tool) && s.result().success()) {
                return s.result();
            }
        }
        return null;
    }

    public boolean ran(String tool) {
        return steps.stream().anyMatch(s -> s.tool().equals(tool));
    }

    /** How many times a tool has run (successfully or not). */
    public int count(String tool) {
        return (int) steps.stream().filter(s -> s.tool().equals(tool)).count();
    }

    /** All successful results of a tool, in order. */
    public List<ToolResult> resultsOf(String tool) {
        return steps.stream().filter(s -> s.tool().equals(tool) && s.result().success()).map(Step::result).toList();
    }

    /** The workspace path a document tool reported ("PATH=reports/x.docx"), or null. */
    public String savedPath() {
        for (int i = steps.size() - 1; i >= 0; i--) {
            Matcher m = PATH.matcher(steps.get(i).result().output());
            if (steps.get(i).result().success() && m.find()) {
                return m.group(1);
            }
        }
        return null;
    }
}
