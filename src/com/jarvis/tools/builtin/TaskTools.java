package com.jarvis.tools.builtin;

import com.jarvis.model.TaskItem;
import com.jarvis.tools.RiskLevel;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolArgs;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolParam;
import com.jarvis.tools.ToolResult;

import com.jarvis.util.TimeParser;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.jarvis.tools.ToolParam.Type.INTEGER;
import static com.jarvis.tools.ToolParam.Type.STRING;

/** The user's to-do list. */
public final class TaskTools {

    private TaskTools() {
    }

    public static class AddTask extends Tool {
        public AddTask() {
            super("add_task", "tasks", "Add an item to the user's to-do list.", RiskLevel.SAFE,
                    ToolParam.required("title", STRING, "What needs to be done"),
                    ToolParam.choice("priority", false, "Priority (default medium)", "low", "medium", "high"),
                    ToolParam.optional("due_date", STRING, "When it is due: 'Friday', 'tomorrow', '14 November' or YYYY-MM-DD"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            String due = null;
            if (args.has("due_date")) {
                try {
                    due = TimeParser.parseDate(args.string("due_date"), LocalDate.now(ctx.zone())).toString();
                } catch (RuntimeException e) {
                    throw new ToolException(e.getMessage() + " Use e.g. 'Friday', 'tomorrow' or '14 November'.");
                }
            }
            TaskItem t = ctx.store().tasks.save(new TaskItem(args.string("title"),
                    TaskItem.Priority.valueOf(args.string("priority", "medium").toUpperCase()), due));
            String when = due == null ? "" : " (due " + LocalDate.parse(due).format(DateTimeFormatter.ofPattern("EEEE d MMMM")) + ")";
            return ToolResult.ok("Task #" + t.getId() + " added to your to-do list: " + t.getTitle() + when
                    + ", priority " + t.getPriority().name().toLowerCase() + ".");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Add task: " + args.string("title");
        }
    }

    public static class ListTasks extends Tool {
        public ListTasks() {
            super("list_tasks", "tasks", "List to-do items.", RiskLevel.SAFE,
                    ToolParam.choice("status", false, "Which tasks (default open)", "open", "done", "all"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) {
            String status = args.string("status", "open");
            List<TaskItem> list = status.equals("all") ? ctx.store().tasks.findAll()
                    : ctx.store().tasks.byStatus(TaskItem.Status.valueOf(status.toUpperCase()));
            if (list.isEmpty()) {
                return ToolResult.ok("No " + (status.equals("all") ? "" : status + " ") + "tasks.");
            }
            StringBuilder sb = new StringBuilder();
            for (TaskItem t : list) {
                sb.append("#").append(t.getId()).append(" [").append(t.getStatus() == TaskItem.Status.DONE ? "x" : " ")
                        .append("] ").append(t.getTitle()).append(" (").append(t.getPriority().name().toLowerCase())
                        .append(t.getDueDate() != null ? ", due " + t.getDueDate() : "").append(")\n");
            }
            return ToolResult.ok(sb.toString().trim());
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "List " + args.string("status", "open") + " tasks";
        }
    }

    public static class CompleteTask extends Tool {
        public CompleteTask() {
            super("complete_task", "tasks", "Mark a to-do item as done.", RiskLevel.SAFE,
                    ToolParam.required("id", INTEGER, "Task id"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            long id = args.integer("id");
            TaskItem t = ctx.store().tasks.findById(id)
                    .orElseThrow(() -> new ToolException("There is no task #" + id + "."));
            t.complete();
            ctx.store().tasks.update(t);
            return ToolResult.ok("Task #" + id + " marked done: " + t.getTitle());
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Complete task #" + args.integer("id");
        }
    }

    public static class DeleteTask extends Tool {
        public DeleteTask() {
            super("delete_task", "tasks", "Permanently delete a to-do item.", RiskLevel.DESTRUCTIVE,
                    ToolParam.required("id", INTEGER, "Task id"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            long id = args.integer("id");
            if (!ctx.store().tasks.delete(id)) {
                throw new ToolException("There is no task #" + id + ".");
            }
            return ToolResult.ok("Task #" + id + " deleted.");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Delete task #" + args.integer("id");
        }
    }
}
