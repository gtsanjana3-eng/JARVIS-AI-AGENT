package com.jarvis.tools.builtin;

import com.jarvis.model.Memory;
import com.jarvis.tools.RiskLevel;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolArgs;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolParam;
import com.jarvis.tools.ToolResult;

import java.util.List;

import static com.jarvis.tools.ToolParam.Type.INTEGER;
import static com.jarvis.tools.ToolParam.Type.STRING;

/**
 * Long-term memory about the user. Saving a memory is SENSITIVE, so the user
 * must approve every fact before JARVIS stores it.
 */
public final class MemoryTools {

    private MemoryTools() {
    }

    public static class Remember extends Tool {
        public Remember() {
            super("remember_fact", "memory",
                    "Store a lasting fact about the user in long-term memory (exam dates, subjects, preferences, "
                            + "goals). The user is asked to approve it first.",
                    RiskLevel.SENSITIVE,
                    ToolParam.required("fact", STRING, "The fact, written as a full sentence"),
                    ToolParam.choice("category", false, "Kind of fact",
                            "study", "schedule", "preference", "personal", "goal", "other"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) {
            Memory m = ctx.store().memories.save(new Memory(args.string("fact"), args.string("category", "other")));
            return ToolResult.ok("Memory #" + m.getId() + " stored: " + m.getContent());
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Remember: \"" + args.string("fact") + "\"";
        }
    }

    public static class Recall extends Tool {
        public Recall() {
            super("recall_memories", "memory", "Look up facts stored in long-term memory about the user.",
                    RiskLevel.SAFE,
                    ToolParam.optional("query", STRING, "Topic to look for; omit to list everything"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) {
            List<Memory> list = args.has("query") ? ctx.store().memories.search(args.string("query"))
                    : ctx.store().memories.findAll();
            if (list.isEmpty()) {
                return ToolResult.ok("No stored memories" + (args.has("query") ? " about '" + args.string("query") + "'." : "."));
            }
            StringBuilder sb = new StringBuilder();
            for (Memory m : list) {
                sb.append("#").append(m.getId()).append(" (").append(m.getCategory()).append(") ")
                        .append(m.getContent()).append('\n');
            }
            return ToolResult.ok(sb.toString().trim());
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Search memory" + (args.has("query") ? " for '" + args.string("query") + "'" : "");
        }
    }

    public static class Forget extends Tool {
        public Forget() {
            super("forget_memory", "memory", "Delete one stored memory by its id number.", RiskLevel.DESTRUCTIVE,
                    ToolParam.required("id", INTEGER, "Memory id"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            long id = args.integer("id");
            if (!ctx.store().memories.delete(id)) {
                throw new ToolException("There is no memory #" + id + ".");
            }
            return ToolResult.ok("Memory #" + id + " forgotten.");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Forget memory #" + args.integer("id");
        }
    }
}
