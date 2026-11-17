package com.jarvis.tools.builtin;

import com.jarvis.ai.ChatMessage;
import com.jarvis.ai.LlmException;
import com.jarvis.search.SearchProvider;
import com.jarvis.search.SearchProvider.SearchHit;
import com.jarvis.tools.RiskLevel;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolArgs;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolParam;
import com.jarvis.tools.ToolResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.jarvis.tools.ToolParam.Type.STRING;

/**
 * Accurate answers from real sources: search the web, read the pages, and answer
 * only from what those pages say — with the sources named.
 */
public final class WebTools {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private WebTools() {
    }

    public static class WebSearch extends Tool {
        private final SearchProvider provider;

        public WebSearch(SearchProvider provider) {
            super("web_search", "research", "Search the web for up-to-date information; returns titles, links and snippets.",
                    RiskLevel.NETWORK_READ, ToolParam.required("query", STRING, "What to search for"));
            this.provider = provider;
        }

        public SearchProvider provider() {
            return provider;
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            List<SearchHit> hits;
            try {
                hits = provider.search(args.string("query"), 6);
            } catch (SearchProvider.SearchException e) {
                throw new ToolException(e.getMessage());
            }
            if (hits.isEmpty()) {
                return ToolResult.ok(provider.name() + " found no results for \"" + args.string("query") + "\".");
            }
            StringBuilder sb = new StringBuilder(provider.name() + " results:\n");
            for (SearchHit h : hits) {
                sb.append("- ").append(h.title()).append(" | ").append(h.url()).append(" | ").append(h.snippet()).append('\n');
            }
            return ToolResult.ok(sb.toString().trim());
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Search " + provider.name() + " for \"" + args.string("query") + "\"";
        }
    }

    public static class ReadWebpage extends Tool {
        public ReadWebpage() {
            super("read_webpage", "research", "Read the main text of a web page (http/https link).", RiskLevel.NETWORK_READ,
                    ToolParam.required("url", STRING, "The page address"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            String url = args.string("url");
            if (!url.matches("(?i)^https?://.+")) {
                throw new ToolException("Only http(s) web pages can be read.");
            }
            java.util.regex.Matcher wiki = java.util.regex.Pattern.compile("(?i)^https?://en\\.wikipedia\\.org/wiki/([^#?]+)").matcher(url);
            if (wiki.find()) {
                // Wikipedia: use its clean-text interface instead of parsing the page layout.
                String title = java.net.URLDecoder.decode(wiki.group(1), StandardCharsets.UTF_8).replace('_', ' ');
                java.util.Map<String, Object> json = ResearchTools.get("action=query&prop=extracts&explaintext=1&redirects=1&titles="
                        + ResearchTools.enc(title));
                for (Object p : com.jarvis.util.Json.asMap(com.jarvis.util.Json.asMap(json.get("query")).get("pages")).values()) {
                    String text = com.jarvis.util.Json.asString(com.jarvis.util.Json.asMap(p).get("extract"), "")
                            .replaceAll("(?m)^=+.*=+$", "").trim();
                    if (text.length() > 200) {
                        return ToolResult.ok("Page " + url + ":\n" + (text.length() > 12000 ? text.substring(0, 12000) + " …" : text));
                    }
                }
            }
            String html;
            try {
                HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "Mozilla/5.0 (JARVIS student project)").GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() != 200) {
                    throw new ToolException("The page returned HTTP " + res.statusCode() + ".");
                }
                html = res.body();
            } catch (IOException | IllegalArgumentException e) {
                throw new ToolException("Could not open " + url + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ToolException("Interrupted.");
            }
            String text = pageText(html);
            if (text.length() < 200) {
                throw new ToolException("That page has no readable text (it may need JavaScript).");
            }
            return ToolResult.ok("Page " + url + ":\n" + (text.length() > 7000 ? text.substring(0, 7000) + " …" : text));
        }

        /** The readable text of an HTML page: no scripts, menus, footers or citation marks. */
        static String pageText(String html) {
            String s = html.replaceAll("(?is)<(script|style|noscript|svg|nav|header|footer|aside|form)\\b.*?</\\1>", " ")
                    .replaceAll("(?is)<!--.*?-->", " ")
                    .replaceAll("(?i)<(br|/p|/h[1-6]|/li|/tr|/div)\\b[^>]*>", "\n")
                    .replaceAll("(?s)<[^>]+>", " ")
                    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                    .replace("&lt;", "<").replace("&gt;", ">").replaceAll("&#\\d+;", " ")
                    .replaceAll("\\[\\d+\\]|\\[edit\\]|\\[citation needed\\]", "");
            StringBuilder out = new StringBuilder();
            for (String line : s.split("\n")) {
                String l = line.replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
                if (l.length() >= 40) { // menu items and buttons are short
                    out.append(l).append('\n');
                }
            }
            return out.toString().trim();
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Read " + args.string("url");
        }
    }

    public static class AnswerFromSources extends Tool {
        private static final Set<String> STOP = Set.of("what", "who", "when", "where", "which", "why", "how", "is", "are",
                "was", "were", "the", "a", "an", "of", "in", "on", "to", "do", "does", "did", "and", "for", "about", "tell", "me");

        public AnswerFromSources() {
            super("answer_from_sources", "research",
                    "Answer a question using only the given source texts (from web_search / read_webpage).",
                    RiskLevel.SAFE,
                    ToolParam.required("question", STRING, "The user's question"),
                    ToolParam.required("sources", STRING, "The source texts, each labelled with its source"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            String q = args.string("question");
            String sources = args.string("sources");
            if (ctx.llm() != null && ctx.llm().status().ready()) {
                try {
                    String answer = ctx.llm().chat(List.of(
                            ChatMessage.system("You answer questions accurately using ONLY the sources provided. "
                                    + "Do not use your own memory. If the sources do not contain the answer, reply exactly: "
                                    + "\"I couldn't find that in the sources.\" Give the answer in 2-4 clear sentences, "
                                    + "include specific facts (names, numbers, dates) from the sources, and do not add "
                                    + "anything the sources do not say."),
                            ChatMessage.user("Question: " + q + "\n\nSources:\n" + sources)), List.of(), null, null).content().trim();
                    if (!answer.isEmpty()) {
                        return ToolResult.ok(answer);
                    }
                } catch (LlmException e) {
                    // fall back to quoting the sources directly
                }
            }
            return ToolResult.ok(extract(q, sources));
        }

        /** Without the AI: the source sentences that share the most words with the question. */
        static String extract(String question, String sources) {
            List<String> words = new ArrayList<>();
            for (String w : question.toLowerCase().split("[^a-z0-9]+")) {
                if (w.length() > 2 && !STOP.contains(w)) {
                    words.add(w);
                }
            }
            record Scored(String sentence, int score, int order) {
            }
            List<Scored> scored = new ArrayList<>();
            String[] sentences = sources.replaceAll("\\s+", " ").split("(?<=[.!?])\\s+(?=[A-Z])");
            for (int i = 0; i < sentences.length; i++) {
                String low = sentences[i].toLowerCase();
                int score = 0;
                for (String w : words) {
                    if (low.contains(w)) {
                        score++;
                    }
                }
                if (score > 0 && sentences[i].length() < 400 && !sentences[i].startsWith("Page ")) {
                    scored.add(new Scored(sentences[i].trim(), score, i));
                }
            }
            if (scored.isEmpty()) {
                return "I couldn't find that in the sources.";
            }
            List<Scored> best = scored.stream().sorted(Comparator.comparingInt(Scored::score).reversed()
                    .thenComparingInt(Scored::order)).limit(3).sorted(Comparator.comparingInt(Scored::order)).toList();
            StringBuilder sb = new StringBuilder();
            best.forEach(b -> sb.append(b.sentence()).append(' '));
            return sb.toString().trim();
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Answer from the sources: " + args.string("question");
        }
    }
}
