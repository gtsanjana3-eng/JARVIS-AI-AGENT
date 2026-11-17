package com.jarvis.agent.workflow;

import com.jarvis.ai.ToolCall;
import com.jarvis.tools.ToolResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Write a leave letter / essay / notes about X in Word (or Notepad...)".
 *
 * Steps: the AI writes the text → save it as a document in the right format →
 * open it in the requested app → confirm.
 */
public class WriteWorkflow extends Workflow {

    private final String kind;
    private String topic;
    private final AppTarget target;
    private boolean asked;

    public WriteWorkflow(String kind, String topic, AppTarget target) {
        super("write a " + kind + " → " + target.label());
        this.kind = kind;
        this.topic = topic;
        this.target = target;
    }

    /** Asks for the details that make the text personal, unless the user already gave some. */
    @Override
    public String question() {
        if (asked || topic.split("\\s+").length >= 4) {
            return null;
        }
        String k = kind.toLowerCase();
        if (k.contains("letter") || k.contains("application") || k.contains("email") || k.contains("mail")) {
            return "Sure. Who is the " + kind + " for, what is the reason, and what name should I sign it with? "
                    + "Or say \"go ahead\" and I'll write a general one.";
        }
        return "Sure. Any points you want me to include in the " + kind + " about " + topic
                + ", or how long it should be? Say \"go ahead\" if not.";
    }

    @Override
    public void answer(String reply) {
        asked = true;
        String r = reply.trim();
        if (!r.matches("(?i)^(go ahead|no|nope|nothing|none|just write it|write it|ok(ay)?|fine|anything)\\W*$")) {
            topic = topic + ". Details from the user: " + r;
        }
    }

    @Override
    public List<String> plannedSteps() {
        return List.of("Write the " + kind + " with the AI",
                "Save it as a ." + target.format() + " document",
                "Open it in " + target.label());
    }

    @Override
    public ToolCall next(WorkflowState st) {
        if (st.stopped()) {
            return null;
        }
        if (!st.ran("write_with_ai")) {
            return call("write_with_ai", "kind", kind, "topic", topic);
        }
        if (!st.ran("create_report")) {
            return call("create_report", "title", title(), "content", st.resultOf("write_with_ai").output(),
                    "format", target.format());
        }
        if (!st.ran("open_file")) {
            return call("open_file", "path", st.savedPath(), "app", target.app());
        }
        return null;
    }

    @Override
    public String summary(WorkflowState st) {
        ToolResult text = st.resultOf("write_with_ai");
        if (text == null) {
            return "I couldn't write the " + kind + ": " + st.last().result().output();
        }
        String path = st.savedPath();
        if (path == null) {
            return "I wrote the " + kind + " but couldn't save it: " + st.last().result().output();
        }
        WorkflowState.Step last = st.last();
        if (last.tool().equals("open_file") && last.denied()) {
            return "I wrote your " + kind + " and saved it to workspace/" + path + ", but didn't open it, as you asked.";
        }
        if (st.resultOf("open_file") == null) {
            return "I saved your " + kind + " to workspace/" + path + ", but couldn't open it in " + target.label() + ": "
                    + last.result().output();
        }
        return "Done. I wrote your " + kind + " and opened it in " + target.label() + " (workspace/" + path
                + "). Check it and edit anything you like.";
    }

    private String title() {
        String about = topic.contains(". Details from the user:") ? topic.substring(0, topic.indexOf(". Details")) : topic;
        String t = Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + (about.equals(kind) ? "" : ": " + about);
        return t.length() > 70 ? t.substring(0, 67) + "…" : t;
    }

    private static ToolCall call(String tool, Object... kv) {
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            args.put((String) kv[i], kv[i + 1]);
        }
        return new ToolCall(tool, args);
    }
}
