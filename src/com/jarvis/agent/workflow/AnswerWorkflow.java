package com.jarvis.agent.workflow;

import com.jarvis.ai.ToolCall;
import com.jarvis.tools.ToolResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Accurate answers to factual questions ("who won…", "what is the population of…"):
 * search the web → read the top pages → pick the sentences that answer the question →
 * the AI writes the answer only from those sentences → reply with the sources named.
 */
public class AnswerWorkflow extends Workflow {

    private static final Set<String> STOP = Set.of("what", "who", "when", "where", "which", "why", "how", "is", "are",
            "was", "were", "the", "a", "an", "of", "in", "on", "to", "do", "does", "did", "and", "for", "about", "tell",
            "me", "can", "you", "please", "give", "current", "latest", "there", "that", "this", "with", "has", "have");
    private static final int MAX_PAGES = 2;

    private final String question;
    private final List<String[]> pages = new ArrayList<>(); // {title, url} chosen to read
    private int pagesTried;

    public AnswerWorkflow(String question) {
        super("answer from sources: " + question);
        this.question = question;
    }

    @Override
    public List<String> plannedSteps() {
        return List.of("Search the web for the question", "Read the top " + MAX_PAGES + " pages",
                "Pick the sentences that answer it", "Write the answer only from those sources");
    }

    @Override
    public ToolCall next(WorkflowState st) {
        if (!st.ran("web_search")) {
            return call("web_search", "query", question);
        }
        ToolResult search = st.resultOf("web_search");
        if (search == null) {
            return null; // search failed: summary explains
        }
        if (pages.isEmpty() && pagesTried == 0) {
            for (String line : search.output().split("\n")) {
                String[] parts = line.startsWith("- ") ? line.substring(2).split(" \\| ", 3) : new String[0];
                if (parts.length >= 2 && pages.size() < 4 && !parts[1].matches("(?i).*(youtube|facebook|instagram|"
                        + "twitter|x\\.com|tiktok|pinterest|reddit)\\..*")) {
                    pages.add(new String[] { parts[0], parts[1] });
                }
            }
        }
        // Read pages until two succeeded (a page that can't be read is skipped, not fatal).
        if (st.resultsOf("read_webpage").size() < MAX_PAGES && pagesTried < pages.size()) {
            return call("read_webpage", "url", pages.get(pagesTried++)[1]);
        }
        if (!st.ran("answer_from_sources")) {
            return call("answer_from_sources", "question", question, "sources", evidence(st));
        }
        return null;
    }

    @Override
    public String summary(WorkflowState st) {
        ToolResult search = st.resultOf("web_search");
        if (search == null) {
            return "I couldn't search for that: " + st.last().result().output();
        }
        ToolResult answer = st.resultOf("answer_from_sources");
        if (answer == null) {
            return "I searched but couldn't put an answer together. " + st.last().result().output();
        }
        StringBuilder sb = new StringBuilder(answer.output());
        List<String> used = new ArrayList<>();
        for (ToolResult r : st.resultsOf("read_webpage")) {
            String url = r.output().substring(5, r.output().indexOf(":\n"));
            for (String[] p : pages) {
                if (p[1].equals(url)) {
                    used.add(p[0] + " — " + url);
                }
            }
        }
        if (used.isEmpty()) {
            used.add(search.output().lines().findFirst().orElse("Search results").replace(" results:", ""));
        }
        sb.append("\n\nSources:\n");
        used.forEach(u -> sb.append("- ").append(u).append('\n'));
        return sb.toString().trim();
    }

    /** The search snippets plus the ~12 sentences from the pages that best match the question. */
    private String evidence(WorkflowState st) {
        List<String> words = keywords(question);
        StringBuilder sb = new StringBuilder("Search snippets:\n");
        st.resultOf("web_search").output().lines().skip(1).limit(5).forEach(l -> {
            String[] p = l.substring(2).split(" \\| ", 3);
            if (p.length == 3) {
                sb.append("- [").append(p[0]).append("] ").append(p[2]).append('\n');
            }
        });
        List<String> sentences = new ArrayList<>();
        for (ToolResult r : st.resultsOf("read_webpage")) {
            String body = r.output().substring(r.output().indexOf(":\n") + 2);
            for (String s : body.replaceAll("\\s+", " ").split("(?<=[.!?])\\s+(?=[A-Z0-9\"'])")) {
                if (s.length() > 30 && s.length() < 450) {
                    sentences.add(s.trim());
                }
            }
        }
        // Rare question words matter most: on the Karnataka page almost every sentence says "Karnataka",
        // but only a few mention "population". (Inverse document frequency weighting.)
        Map<String, Double> weight = new LinkedHashMap<>();
        for (String w : words) {
            long df = sentences.stream().filter(s -> s.toLowerCase(Locale.ROOT).contains(w)).count();
            weight.put(w, Math.log((sentences.size() + 1.0) / (df + 1.0)) + 0.3);
        }
        boolean wantsNumber = question.toLowerCase(Locale.ROOT).matches(".*\\b(how many|how much|population|when|"
                + "year|age|old|price|cost|height|tall|distance|far|area|size|number|rank|score)\\b.*");
        record Scored(String text, double score, int order) {
        }
        List<Scored> all = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String low = sentences.get(i).toLowerCase(Locale.ROOT);
            double score = 0;
            for (String w : words) {
                if (low.contains(w)) {
                    score += weight.get(w);
                }
            }
            if (score > 0 && wantsNumber && low.matches(".*\\d.*")) {
                score += 0.8;
            }
            if (score > 0) {
                all.add(new Scored(sentences.get(i), score, i));
            }
        }
        List<Scored> best = all.stream().sorted(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparingInt(Scored::order)).limit(12).sorted(Comparator.comparingInt(Scored::order)).toList();
        if (!best.isEmpty()) {
            sb.append("\nFrom the pages:\n");
            best.forEach(b -> sb.append("- ").append(b.text()).append('\n'));
        }
        return sb.toString();
    }

    static List<String> keywords(String q) {
        List<String> out = new ArrayList<>();
        for (String w : q.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() > 2 && !STOP.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    private static ToolCall call(String tool, Object... kv) {
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            args.put((String) kv[i], kv[i + 1]);
        }
        return new ToolCall(tool, args);
    }
}
