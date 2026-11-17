package com.jarvis.tools.builtin;

import com.jarvis.model.Reminder;
import com.jarvis.tools.RiskLevel;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolArgs;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolParam;
import com.jarvis.tools.ToolResult;
import com.jarvis.util.TimeParser;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.jarvis.tools.ToolParam.Type.INTEGER;
import static com.jarvis.tools.ToolParam.Type.STRING;

/** Reminders are stored in the database and fired by the ReminderService at the right time. */
public final class ReminderTools {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");

    private ReminderTools() {
    }

    static String format(long epochMs, ToolContext ctx) {
        return Instant.ofEpochMilli(epochMs).atZone(ctx.zone()).format(FMT);
    }

    public static class CreateReminder extends Tool {
        public CreateReminder() {
            super("create_reminder", "reminders",
                    "Schedule a reminder. JARVIS will alert and speak it at that time while the app is open.",
                    RiskLevel.SAFE,
                    ToolParam.required("text", STRING, "What to remind the user about"),
                    ToolParam.required("when", STRING,
                            "Copy the user's own wording, do not convert it: 'in 20 minutes', 'tomorrow at 9am', "
                                    + "'17:30', or 'YYYY-MM-DD HH:MM'"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            ZonedDateTime now = ZonedDateTime.now(ctx.zone());
            ZonedDateTime due;
            try {
                due = TimeParser.parse(args.string("when"), now);
            } catch (RuntimeException e) {
                throw new ToolException(e.getMessage());
            }
            if (due.isBefore(now.minusMinutes(1))) {
                throw new ToolException("That time (" + due.format(FMT) + ") is in the past.");
            }
            Reminder r = ctx.store().reminders.save(new Reminder(args.string("text"), due.toInstant().toEpochMilli()));
            return ToolResult.ok("Reminder #" + r.getId() + " set for " + due.format(FMT) + ": " + r.getText());
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Remind \"" + args.string("text") + "\" " + args.string("when");
        }
    }

    public static class ListReminders extends Tool {
        public ListReminders() {
            super("list_reminders", "reminders", "List upcoming (pending) reminders.", RiskLevel.SAFE);
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) {
            List<Reminder> list = ctx.store().reminders.pending();
            if (list.isEmpty()) {
                return ToolResult.ok("No upcoming reminders.");
            }
            StringBuilder sb = new StringBuilder();
            for (Reminder r : list) {
                sb.append("#").append(r.getId()).append(" ").append(format(r.getDueAt(), ctx)).append(" — ")
                        .append(r.getText()).append('\n');
            }
            return ToolResult.ok(sb.toString().trim());
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "List reminders";
        }
    }

    public static class CancelReminder extends Tool {
        public CancelReminder() {
            super("cancel_reminder", "reminders", "Cancel a pending reminder by id.", RiskLevel.SENSITIVE,
                    ToolParam.required("id", INTEGER, "Reminder id"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            long id = args.integer("id");
            Reminder r = ctx.store().reminders.findById(id)
                    .orElseThrow(() -> new ToolException("There is no reminder #" + id + "."));
            if (r.getStatus() != Reminder.Status.PENDING) {
                throw new ToolException("Reminder #" + id + " is already " + r.getStatus().name().toLowerCase() + ".");
            }
            r.cancel();
            ctx.store().reminders.update(r);
            return ToolResult.ok("Reminder #" + id + " cancelled.");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Cancel reminder #" + args.integer("id");
        }
    }
}
