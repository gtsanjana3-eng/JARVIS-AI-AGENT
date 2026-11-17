package com.jarvis.agent;

import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The "selecting tools" stage. Before the model is asked anything, Java picks
 * the tool categories this request could need. Knowledge questions and
 * explanations get no tools at all (the model just answers, quickly and
 * without detours); action requests get only the relevant few tools. A small
 * tool list keeps a small local model accurate and its prompt short.
 */
final class ToolSelector {

    private static final Map<String, Pattern> TRIGGERS = new LinkedHashMap<>();

    static {
        TRIGGERS.put("study", p("\\b(study|revision)\\s*(plan|schedule|timetable)|\\btimetable\\b|\\bexams?\\b"));
        TRIGGERS.put("reminders", p("\\bremind(er|ers)?\\b|\\balarm\\b"));
        TRIGGERS.put("tasks", p("\\bto-?do\\b|\\btasks?\\b"));
        TRIGGERS.put("notes", p("\\bnotes?\\b|\\bjot\\b|\\bwrite (it |this |that )?down\\b"));
        TRIGGERS.put("memory", p("\\bremember\\b|\\bforget\\b|\\bwhat do you (know|remember)\\b|\\bmemor(y|ies)\\b|\\babout me\\b"));
        TRIGGERS.put("math", p("\\d\\s*(%|percent|[-+*/^x×÷])\\s*\\d|\\bcalculate\\b|\\bsquare root\\b|\\d+\\s*%\\s*of\\b"));
        TRIGGERS.put("time", p("\\b(what|which) (time|day|date)\\b|\\btoday'?s date\\b|\\bcurrent (time|date)\\b"));
        TRIGGERS.put("research", p("\\bresearch\\b|\\bwikipedia\\b|\\blook (it )?up\\b|\\bsearch (for|about)\\b|\\bfind (out|information)\\b"));
        TRIGGERS.put("files", p("\\bfiles?\\b|\\bfolders?\\b|\\bdocuments?\\b|\\breport\\b|\\bworkspace\\b|\\borgani[sz]e\\b|"
                + "\\bnotepad\\b|\\b(ms )?word\\b|\\.(md|txt|csv|docx)\\b|\\bsave (it|this|that)\\b"));
        TRIGGERS.put("desktop", p("\\b(open|launch|start|run|close)\\s+\\w|\\bplay\\b|\\b(youtube|spotify|whatsapp|e-?mail|gmail|"
                + "outlook|maps|directions|navigate|settings|wi-?fi|bluetooth|chrome|browser|vs ?code|app|application|"
                + "screenshot|lock)\\b|\\bsend\\b|\\bmessage\\b"));
        TRIGGERS.put("info", p("\\bweather\\b|\\btemperature\\b|\\brain(ing)?\\b|\\bforecast\\b|\\bnews\\b|\\bheadlines?\\b"));
        TRIGGERS.put("system", p("\\b(cpu|ram|memory usage|system status|diagnostics?|battery)\\b"));
    }

    private ToolSelector() {
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /** Tool categories the text refers to. */
    static Set<String> categories(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) {
            return out;
        }
        TRIGGERS.forEach((category, pattern) -> {
            if (pattern.matcher(text).find()) {
                out.add(category);
            }
        });
        if (out.contains("desktop")) {
            out.add("files"); // "write it in Notepad" = create a document + open it
        }
        return out;
    }

    /**
     * Tools to offer for this request. The previous user message is included so short follow-ups
     * ("Bengaluru", "yes, at 6 pm") keep the tools of the task they belong to.
     */
    static List<Tool> select(String request, String previousUserMessage, ToolRegistry registry) {
        Set<String> cats = categories(request);
        cats.addAll(categories(previousUserMessage));
        List<Tool> out = new ArrayList<>();
        for (Tool t : registry.all()) {
            // write_with_ai is for workflows: in conversation the model writes text itself.
            if (cats.contains(t.getCategory()) && !t.getName().equals("write_with_ai")) {
                out.add(t);
            }
        }
        return out;
    }
}
