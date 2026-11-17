package com.jarvis.tools.builtin;

import com.jarvis.tools.RiskLevel;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolArgs;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolParam;
import com.jarvis.tools.ToolResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.jarvis.tools.ToolParam.Type.NUMBER;
import static com.jarvis.tools.ToolParam.Type.STRING;

/**
 * Builds a day-by-day study timetable up to an exam date with a real
 * algorithm (weighted round-robin + revision days) and saves it as a
 * Markdown document in workspace/study-plans/.
 */
public class StudyPlanTool extends Tool {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM");

    public StudyPlanTool() {
        super("create_study_plan", "study",
                "Create a day-by-day study timetable from today until an exam date and save it as a document.",
                RiskLevel.SAFE,
                ToolParam.required("subjects", STRING, "Comma-separated subjects, e.g. Maths, Physics, Java"),
                ToolParam.required("exam_date", STRING, "Exam date as YYYY-MM-DD"),
                ToolParam.optional("hours_per_day", NUMBER, "Study hours per day (default 3)"),
                ToolParam.optional("focus_subjects", STRING, "Comma-separated weaker subjects that need extra time"));
    }

    @Override
    protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
        List<String> subjects = split(args.string("subjects"));
        if (subjects.isEmpty()) {
            throw new ToolException("Please give at least one subject.");
        }
        Set<String> focus = new LinkedHashSet<>();
        for (String f : split(args.string("focus_subjects", ""))) {
            subjects.stream().filter(s -> s.equalsIgnoreCase(f)).findFirst().ifPresent(focus::add);
        }
        LocalDate exam;
        try {
            exam = LocalDate.parse(args.string("exam_date"));
        } catch (DateTimeParseException e) {
            throw new ToolException("exam_date must be in the form YYYY-MM-DD.");
        }
        LocalDate today = LocalDate.now(ctx.zone());
        int days = (int) ChronoUnit.DAYS.between(today, exam);
        if (days < 1) {
            throw new ToolException("The exam date must be after today (" + today + ").");
        }
        if (days > 120) {
            throw new ToolException("The exam is more than 120 days away; ask again closer to the date.");
        }
        double hours = Math.max(0.5, Math.min(12, args.number("hours_per_day", 3)));

        // Weighted rotation: weaker subjects appear twice per cycle.
        List<String> cycle = new ArrayList<>();
        for (String s : subjects) {
            cycle.add(s);
        }
        for (String s : focus) {
            cycle.add(cycle.indexOf(s) + 1, s);
        }

        int revisionDays = days >= 6 ? 2 : days >= 3 ? 1 : 0;
        int studyDays = days - revisionDays;
        boolean twoSessions = hours >= 2;
        String h1 = fmtHours(twoSessions ? hours / 2 : hours);

        StringBuilder md = new StringBuilder();
        md.append("# Study plan — exam on ").append(exam.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")))
                .append("\n\n");
        md.append("- Subjects: ").append(String.join(", ", subjects)).append('\n');
        if (!focus.isEmpty()) {
            md.append("- Extra focus: ").append(String.join(", ", focus)).append('\n');
        }
        md.append("- ").append(days).append(" day(s) left, ").append(fmtHours(hours)).append(" per day\n\n");
        md.append("| Day | Date | ").append(twoSessions ? "Session 1 (" + h1 + ") | Session 2 (" + h1 + ") |"
                : "Session (" + h1 + ") |").append('\n');
        md.append(twoSessions ? "|---|---|---|---|\n" : "|---|---|---|\n");

        int slot = 0;
        for (int d = 0; d < days; d++) {
            LocalDate date = today.plusDays(d);
            String s1;
            String s2;
            if (d >= studyDays) {
                boolean last = d == days - 1;
                s1 = last ? "Light review of summaries and formulas" : "Full revision: " + String.join(", ", subjects);
                s2 = last ? "Rest, prepare materials, sleep early" : "Timed mock paper + check mistakes";
            } else {
                s1 = cycle.get(slot++ % cycle.size()) + " — learn / revise core topics";
                if ((d + 1) % 4 == 0) {
                    s2 = "Mixed practice questions (all subjects)";
                } else {
                    String next = cycle.get(slot % cycle.size());
                    if (next.equals(s1.substring(0, s1.indexOf(" —"))) && cycle.size() > 1) {
                        slot++;
                        next = cycle.get(slot % cycle.size());
                    }
                    slot++;
                    s2 = next + " — practice problems";
                }
            }
            md.append("| ").append(d + 1).append(" | ").append(date.format(DAY)).append(" | ").append(s1);
            md.append(twoSessions ? " | " + s2 + " |\n" : " |\n");
        }
        md.append("\nTips: take a 10-minute break every hour, keep a list of mistakes, and sleep at least 7 hours.\n");

        Path dir = ctx.workspace().resolve("study-plans");
        Files.createDirectories(dir);
        Path file = FileTools.uniqueTarget(dir.resolve("study-plan-" + exam + ".md"));
        Files.writeString(file, md.toString(), StandardCharsets.UTF_8);
        return ToolResult.document("Study plan saved to " + ctx.workspace().display(file) + ".", md.toString());
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        // "Maths, Data and Algorithms" keeps "Data and Algorithms" whole; "Maths and Physics" splits on "and".
        String separator = s.contains(",") || s.contains(";") ? "[,;]" : "\\band\\b";
        for (String part : Arrays.asList(s.split(separator))) {
            String t = part.trim();
            if (!t.isEmpty() && out.stream().noneMatch(t::equalsIgnoreCase)) {
                out.add(t);
            }
        }
        return out;
    }

    private static String fmtHours(double h) {
        return (h == Math.rint(h) ? String.valueOf((int) h) : String.valueOf(Math.round(h * 10) / 10.0)) + " h";
    }

    @Override
    public String describeCall(ToolArgs args) {
        return "Plan study for " + args.string("subjects") + " until " + args.string("exam_date");
    }
}
