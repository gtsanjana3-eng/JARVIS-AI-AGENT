package com.jarvis.tools.builtin;

import com.jarvis.tools.RiskLevel;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolArgs;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolParam;
import com.jarvis.tools.ToolResult;
import com.jarvis.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.jarvis.tools.ToolParam.Type.INTEGER;
import static com.jarvis.tools.ToolParam.Type.STRING;

/**
 * Real research using Wikipedia's free public API (no key needed). Read-only:
 * only the search words are sent, never personal data.
 */
public final class ResearchTools {

    private static final String API = "https://en.wikipedia.org/w/api.php";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private ResearchTools() {
    }

    static Map<String, Object> get(String query) throws ToolException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + "?" + query + "&format=json&utf8=1"))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "JARVIS-Student-Project/2.0 (Java OOP mini-project)")
                .GET().build();
        try {
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new ToolException("Wikipedia returned HTTP " + res.statusCode() + ".");
            }
            return Json.parse(res.body());
        } catch (IOException e) {
            throw new ToolException("Could not reach Wikipedia (check your internet connection): " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolException("Request interrupted.");
        }
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** First ~3 sentences of an article's introduction, or "" if unavailable. */
    static String intro(String title) {
        try {
            Map<String, Object> json = get("action=query&prop=extracts&explaintext=1&exintro=1&exsentences=3&redirects=1&titles="
                    + enc(title));
            for (Object p : Json.asMap(Json.asMap(json.get("query")).get("pages")).values()) {
                return Json.asString(Json.asMap(p).get("extract"), "").replaceAll("\\s+", " ").trim();
            }
        } catch (ToolException ignored) {
            // the search results alone are still useful
        }
        return "";
    }

    public static class SearchWikipedia extends Tool {
        public SearchWikipedia() {
            super("search_wikipedia", "research",
                    "Search Wikipedia for articles about a topic. Returns titles and short snippets; then call "
                            + "read_wikipedia_article on the most relevant title to get the facts.",
                    RiskLevel.NETWORK_READ,
                    ToolParam.required("query", STRING, "Search words"),
                    ToolParam.optional("limit", INTEGER, "Number of results, 1-8 (default 5)"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            long limit = Math.max(1, Math.min(8, args.integer("limit", 5)));
            Map<String, Object> json = get("action=query&list=search&srsearch=" + enc(args.string("query"))
                    + "&srlimit=" + limit);
            List<Object> hits = Json.asList(Json.asMap(json.get("query")).get("search"));
            if (hits.isEmpty()) {
                return ToolResult.ok("Wikipedia has no results for '" + args.string("query") + "'.");
            }
            StringBuilder sb = new StringBuilder();
            // Include the opening facts of the best match, so one step usually answers the question.
            String top = Json.asString(Json.asMap(hits.get(0)).get("title"), "");
            String intro = intro(top);
            if (!intro.isEmpty()) {
                sb.append("Top article \"").append(top).append("\": ").append(intro).append("\n\nOther results:\n");
            }
            for (Object h : hits) {
                Map<String, Object> m = Json.asMap(h);
                String snippet = Json.asString(m.get("snippet"), "").replaceAll("<[^>]+>", "")
                        .replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'");
                sb.append("- ").append(Json.asString(m.get("title"), "")).append(": ").append(snippet).append("…\n");
            }
            return ToolResult.ok(sb.toString().trim());
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Search Wikipedia for \"" + args.string("query") + "\"";
        }
    }

    public static class ReadWikipedia extends Tool {
        public ReadWikipedia() {
            super("read_wikipedia_article", "research",
                    "Read the text of a Wikipedia article by exact title (use search_wikipedia first to find it).",
                    RiskLevel.NETWORK_READ,
                    ToolParam.required("title", STRING, "Exact article title"),
                    ToolParam.choice("detail", false, "summary (default), full, or complete (whole article, for reports)",
                            "summary", "full", "complete"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            String detail = args.string("detail", "summary");
            boolean full = !detail.equals("summary");
            Map<String, Object> json = get("action=query&prop=extracts&explaintext=1&redirects=1"
                    + (full ? "" : "&exintro=1") + "&titles=" + enc(args.string("title")));
            Map<String, Object> pages = Json.asMap(Json.asMap(json.get("query")).get("pages"));
            for (Object p : pages.values()) {
                Map<String, Object> page = Json.asMap(p);
                if (page.containsKey("missing")) {
                    break;
                }
                String text = Json.asString(page.get("extract"), "").trim();
                if (text.isEmpty()) {
                    break;
                }
                int max = detail.equals("complete") ? 60000 : full ? 8000 : 3000;
                if (text.length() > max) {
                    text = text.substring(0, max) + "… [truncated]";
                }
                return ToolResult.ok("Wikipedia — " + Json.asString(page.get("title"), "") + ":\n" + text);
            }
            throw new ToolException("No Wikipedia article titled '" + args.string("title")
                    + "'. Use search_wikipedia to find the exact title.");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Read Wikipedia: " + args.string("title");
        }
    }
}
