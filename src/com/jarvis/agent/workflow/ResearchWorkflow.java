package com.jarvis.agent.workflow;

import com.jarvis.ai.ToolCall;
import com.jarvis.tools.ToolResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Research X (and put it in Word / Notepad / the browser / VS Code)".
 *
 * Asks first if the destination or depth is missing, then: search Wikipedia → pick the most relevant
 * article → read the whole article (plus related articles for a detailed report) → build a structured
 * report (key points, summary, sections, related topics, sources) → save it → open it in the requested
 * app → summarise. All in Java, with no AI call, so it takes seconds.
 */
public class ResearchWorkflow extends Workflow {

    /** How much to write. */
    public enum Depth {
        BRIEF("a short summary", 4, 3),
        STANDARD("a standard report", 10, 900),
        DETAILED("a detailed multi-page report", 40, 4000);

        final String label;
        final int maxSections;
        final int perSection; // sentences for BRIEF, characters otherwise

        Depth(String label, int maxSections, int perSection) {
            this.label = label;
            this.maxSections = maxSections;
            this.perSection = perSection;
        }
    }

    private static final Set<String> SKIP_SECTIONS = Set.of("see also", "references", "external links", "notes",
            "further reading", "bibliography", "sources", "citations", "footnotes", "gallery", "works cited",
            "explanatory notes", "general and cited references");
    private static final Set<String> STOP = Set.of("the", "and", "for", "about", "with", "from", "into", "what", "how");

    private final String topic;
    private AppTarget target;   // null = just tell me
    private Depth depth;        // null until known
    private boolean targetKnown;
    private boolean asked;

    private String mainTitle;
    private final List<String> related = new ArrayList<>();

    public ResearchWorkflow(ResearchRequest r) {
        super("research " + r.topic());
        this.topic = r.topic();
        this.target = r.target();
        this.depth = r.depth();
        this.targetKnown = r.targetGiven();
        if (targetKnown && target == null && depth == null) {
            depth = Depth.BRIEF; // "just tell me" → a spoken summary
        }
    }

    // ------------------------------------------------------------------ asking first

    @Override
    public String question() {
        if (asked || (targetKnown && depth != null)) {
            return null;
        }
        if (!targetKnown && depth == null) {
            return "Sure, I'll research " + topic + ". Should I put it in a Word document, Notepad or the browser, "
                    + "or just tell you here? And do you want a short summary or a detailed multi-page report?";
        }
        if (!targetKnown) {
            return "I'll prepare " + depth.label + " on " + topic + ". Should I put it in Word, Notepad or the browser, "
                    + "or just tell you here?";
        }
        return "I'll research " + topic + " and put it in " + target.label() + ". Do you want a short summary, "
                + "a standard report, or a detailed multi-page report?";
    }

    @Override
    public void answer(String reply) {
        asked = true;
        if (!targetKnown) {
            AppTarget t = AppTarget.find(reply).orElse(null);
            if (t != null) {
                target = t;
            } else if (!ResearchRequest.wantsJustAnswer(reply) && ResearchRequest.depthOf(reply) == Depth.DETAILED) {
                target = AppTarget.WORD; // a multi-page report needs a document
            }
            targetKnown = true;
        }
        if (depth == null) {
            Depth d = ResearchRequest.depthOf(reply);
            depth = d != null ? d : reply.toLowerCase(Locale.ROOT).contains("standard") || target != null
                    ? Depth.STANDARD : Depth.BRIEF;
        }
    }

    @Override
    public String intent() {
        return "research " + topic + (target == null ? "" : " → " + target.label())
                + (depth == null ? "" : " (" + depth.name().toLowerCase() + ")");
    }

    @Override
    public List<String> plannedSteps() {
        List<String> steps = new ArrayList<>(List.of(
                "Search Wikipedia for \"" + topic + "\"",
                "Choose the most relevant article and read all of it"));
        if (depth == Depth.DETAILED) {
            steps.add("Read related articles for extra depth");
        }
        if (target != null) {
            steps.add("Write " + depth.label + " (." + target.format() + ")");
            steps.add("Open it in " + target.label());
        }
        steps.add("Summarise the findings");
        return steps;
    }

    // ------------------------------------------------------------------ steps

    @Override
    public ToolCall next(WorkflowState st) {
        // A related article that can't be read is skipped; any other failure stops the workflow honestly.
        boolean relatedReadFailed = st.last() != null && st.last().tool().equals("read_wikipedia_article")
                && st.count("read_wikipedia_article") > 1 && !st.last().denied();
        if (st.stopped() && !relatedReadFailed) {
            return null;
        }
        if (depth == null) {
            depth = target == null ? Depth.BRIEF : Depth.STANDARD;
        }
        if (!st.ran("search_wikipedia")) {
            return call("search_wikipedia", "query", topic, "limit", 10);
        }
        if (depth == Depth.DETAILED && mainTitle == null && st.count("search_wikipedia") == 1) {
            // A second search finds the sub-topic articles ("History of …") for a detailed report.
            List<String> first = titles(st.resultOf("search_wikipedia"));
            String main = bestTitle(first, topic);
            if (main != null) {
                return call("search_wikipedia", "query", "history of " + main.replaceAll("\\s*\\(.*\\)$", ""), "limit", 10);
            }
        }
        if (mainTitle == null) {
            List<ToolResult> searches = st.resultsOf("search_wikipedia");
            mainTitle = bestTitle(titles(searches.get(0)), topic);
            List<String> titles = new ArrayList<>();
            searches.forEach(r -> titles.addAll(titles(r)));
            if (mainTitle == null) {
                return null;
            }
            if (depth == Depth.DETAILED) {
                // Only genuinely related articles: the title must contain the main subject ("History of the
                // Taj Mahal"), and not be a different thing with the same name ("Taj Mahal (musician)").
                String main = mainTitle.toLowerCase(Locale.ROOT).replaceAll("\\s*\\(.*\\)$", "");
                for (String t : titles) {
                    String lt = t.toLowerCase(Locale.ROOT);
                    boolean subTopic = lt.matches("(history|architecture|culture|economy|geography|politics|demographics|"
                            + "timeline|outline|science|applications|ethics|types|impact) of (the )?"
                            + java.util.regex.Pattern.quote(main) + "( .*)?");
                    if (!t.equals(mainTitle) && related.size() < 2 && subTopic) {
                        related.add(t);
                    }
                }
            }
            return call("read_wikipedia_article", "title", mainTitle, "detail", "complete");
        }
        int readsDone = st.count("read_wikipedia_article");
        if (readsDone <= related.size()) {
            return call("read_wikipedia_article", "title", related.get(readsDone - 1), "detail", "summary");
        }
        if (target == null) {
            return null;
        }
        if (!st.ran("create_report")) {
            return call("create_report", "title", mainTitle, "content", report(st), "format", target.format());
        }
        if (!st.ran("open_file")) {
            return call("open_file", "path", st.savedPath(), "app", target.app());
        }
        return null;
    }

    @Override
    public String summary(WorkflowState st) {
        if (st.resultOf("search_wikipedia") == null) {
            return "I couldn't research that: " + st.last().result().output();
        }
        if (mainTitle == null) {
            return "I searched Wikipedia but found nothing about " + topic + ". Could you rephrase the topic?";
        }
        String text = articleText(st, mainTitle);
        if (text == null) {
            return "I found the article \"" + mainTitle + "\" but couldn't read it: " + st.last().result().output();
        }
        String brief = firstSentences(intro(text), 2);
        if (target == null) {
            int n = depth == Depth.BRIEF ? 4 : 8;
            return "Here's what I found about " + mainTitle + ". " + firstSentences(intro(text), n)
                    + "\n\nIf you'd like a full report, say \"research " + topic + " in Word\".";
        }
        String path = st.savedPath();
        if (path == null) {
            return brief + " However, I couldn't save the report: " + st.last().result().output();
        }
        WorkflowState.Step last = st.last();
        if (last.tool().equals("open_file") && last.denied()) {
            return "I saved " + depth.label + " on " + mainTitle + " to workspace/" + path
                    + " but didn't open it, as you asked. In short: " + brief;
        }
        if (st.resultOf("open_file") == null) {
            return "I saved the report to workspace/" + path + ", but couldn't open it in " + target.label() + ": "
                    + last.result().output() + " In short: " + brief;
        }
        return "Done. I researched " + mainTitle + (related.isEmpty() ? "" : " and " + related.size() + " related topics")
                + ", wrote " + depth.label + " and opened it in " + target.label() + ". In short: " + brief;
    }

    // ------------------------------------------------------------------ choosing the article

    static List<String> titles(ToolResult search) {
        List<String> out = new ArrayList<>();
        if (search == null) {
            return out;
        }
        for (String line : search.output().split("\n")) {
            if (line.startsWith("- ") && line.contains(":")) {
                out.add(line.substring(2, line.indexOf(':')).trim());
            }
        }
        return out;
    }

    static int relevance(String title, String topic) {
        String t = title.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String w : topic.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() > 2 && !STOP.contains(w) && t.contains(w)) {
                score++;
            }
        }
        return score;
    }

    /** The search hit whose title best matches the topic (an exact match wins), else the first hit. */
    static String bestTitle(List<String> titles, String topic) {
        if (titles.isEmpty()) {
            return null;
        }
        String best = titles.get(0);
        int bestScore = -1;
        for (String t : titles) {
            if (t.equalsIgnoreCase(topic)) {
                return t;
            }
            int s = relevance(t, topic) * 10 - (t.toLowerCase().contains("disambiguation") ? 100 : 0) - t.length() / 20;
            if (s > bestScore) {
                best = t;
                bestScore = s;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ building the report

    private String articleText(WorkflowState st, String title) {
        for (ToolResult r : st.resultsOf("read_wikipedia_article")) {
            if (r.output().startsWith("Wikipedia — " + title + ":")) {
                String out = r.output();
                return out.substring(out.indexOf('\n') + 1).replace("… [truncated]", "");
            }
        }
        return null;
    }

    static String intro(String text) {
        int h = text.indexOf("\n==");
        return (h < 0 ? text : text.substring(0, h)).trim();
    }

    static String firstSentences(String text, int n) {
        String flat = text.replaceAll("\\s+", " ").trim();
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String s : flat.split("(?<=[.!?])\\s+(?=[A-Z0-9\"'(])")) {
            sb.append(s).append(' ');
            if (++count >= n) {
                break;
            }
        }
        return sb.toString().trim();
    }

    /** A section of the article: heading, level (2 = main, 3+ = sub-section) and body text. */
    private record Section(String heading, int level, String body) {
    }

    private static List<Section> sections(String text) {
        List<Section> out = new ArrayList<>();
        String heading = null;
        int level = 2;
        StringBuilder body = new StringBuilder();
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (l.matches("^=={1,4}[^=].*?=+$")) {
                if (heading != null) {
                    out.add(new Section(heading, level, body.toString().trim()));
                }
                level = l.indexOf(l.replaceFirst("^=+", "").charAt(0));
                heading = l.replaceAll("^=+|=+$", "").trim();
                body.setLength(0);
            } else if (heading != null) {
                body.append(l).append('\n');
            }
        }
        if (heading != null) {
            out.add(new Section(heading, level, body.toString().trim()));
        }
        return out;
    }

    private String report(WorkflowState st) {
        String text = articleText(st, mainTitle);
        List<Section> all = new ArrayList<>();
        boolean skipping = false;
        for (Section s : sections(text)) {
            if (s.level() == 2) {
                skipping = SKIP_SECTIONS.contains(s.heading().toLowerCase(Locale.ROOT));
            }
            if (!skipping) {
                all.add(s);
            }
        }
        StringBuilder md = new StringBuilder();

        if (depth != Depth.BRIEF) {
            md.append("## Key points\n");
            int points = 0;
            for (Section s : all) {
                if (s.level() == 2 && s.body().length() > 60 && points < 8) {
                    md.append("- ").append(s.heading()).append(": ").append(firstSentences(s.body(), 1)).append('\n');
                    points++;
                }
            }
            md.append('\n');
        }

        md.append("## Summary\n");
        String[] paras = intro(text).split("\n+");
        for (int i = 0; i < Math.min(depth == Depth.BRIEF ? 2 : 6, paras.length); i++) {
            if (!paras[i].isBlank()) {
                md.append(paras[i].trim()).append("\n\n");
            }
        }

        int used = 0;
        int budget = depth == Depth.DETAILED ? 30000 : depth == Depth.STANDARD ? 9000 : 2000;
        for (Section s : all) {
            if (used >= depth.maxSections || budget <= 0 || s.body().length() < 40) {
                continue;
            }
            if (depth != Depth.DETAILED && s.level() > 2) {
                continue;
            }
            String body = depth == Depth.BRIEF ? firstSentences(s.body(), depth.perSection)
                    : clip(s.body(), Math.min(depth.perSection, budget));
            md.append(s.level() > 2 ? "### " : "## ").append(s.heading()).append('\n');
            for (String p : body.split("\n+")) {
                if (!p.isBlank()) {
                    md.append(p.trim()).append("\n\n");
                }
            }
            budget -= body.length();
            used++;
        }

        if (!related.isEmpty()) {
            md.append("## Related topics\n");
            for (String r : related) {
                String rt = articleText(st, r);
                if (rt != null) {
                    md.append("### ").append(r).append('\n').append(firstSentences(intro(rt), 4)).append("\n\n");
                }
            }
        }

        md.append("## Sources\n- Wikipedia: ").append(url(mainTitle)).append('\n');
        for (String r : related) {
            md.append("- Wikipedia: ").append(url(r)).append('\n');
        }
        md.append("- Researched by JARVIS on ").append(LocalDate.now()).append('\n');
        return md.toString();
    }

    private static String clip(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        String cut = text.substring(0, max);
        int end = Math.max(cut.lastIndexOf(". "), cut.lastIndexOf(".\n"));
        return end > max / 2 ? cut.substring(0, end + 1) : cut + "…";
    }

    private static String url(String title) {
        return "https://en.wikipedia.org/wiki/" + title.replace(' ', '_');
    }

    private static ToolCall call(String tool, Object... kv) {
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            args.put((String) kv[i], kv[i + 1]);
        }
        return new ToolCall(tool, args);
    }
}
