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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jarvis.tools.ToolParam.Type.STRING;

/**
 * Live information from free public services that need no API key:
 * weather from Open-Meteo and headlines from Google News RSS.
 */
public final class InfoTools {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private InfoTools() {
    }

    static String get(String url, String service) throws ToolException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("User-Agent", "JARVIS-Student-Project/2.0").GET().build();
        try {
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) {
                throw new ToolException(service + " returned HTTP " + res.statusCode() + ".");
            }
            return res.body();
        } catch (IOException e) {
            throw new ToolException("Could not reach " + service + " (check your internet connection).");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolException("Request interrupted.");
        }
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static class Weather extends Tool {
        public Weather() {
            super("get_weather", "info", "Get the current weather and today's/tomorrow's forecast for a city.",
                    RiskLevel.NETWORK_READ, ToolParam.required("city", STRING, "City name, e.g. Bengaluru"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            String city = RENAMED.getOrDefault(args.string("city").toLowerCase().trim(), args.string("city"));
            Map<String, Object> geo = Json.parse(get("https://geocoding-api.open-meteo.com/v1/search?count=10&name="
                    + enc(city), "the weather service"));
            List<Object> hits = Json.asList(geo.get("results"));
            if (hits.isEmpty()) {
                throw new ToolException("I couldn't find a place called '" + city + "'.");
            }
            // Prefer places in the user's own country, then the biggest ("Bangalore" = Bengaluru, India,
            // not a small town elsewhere with the same name).
            String home = homeCountry(ctx);
            Map<String, Object> place = null;
            double bestScore = -1;
            for (Object o : hits) {
                Map<String, Object> h = Json.asMap(o);
                double pop = h.get("population") instanceof Number n ? n.doubleValue() : 0;
                double score = (home.equalsIgnoreCase(Json.asString(h.get("country_code"), "")) ? 1e12 : 0) + pop;
                if (score > bestScore) {
                    bestScore = score;
                    place = h;
                }
            }
            double lat = ((Number) place.get("latitude")).doubleValue();
            double lon = ((Number) place.get("longitude")).doubleValue();
            String name = Json.asString(place.get("name"), city) + ", " + Json.asString(place.get("country"), "");
            Map<String, Object> w = Json.parse(get("https://api.open-meteo.com/v1/forecast?latitude=" + lat
                    + "&longitude=" + lon + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,"
                    + "weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,"
                    + "precipitation_probability_max,weather_code&timezone=auto&forecast_days=2", "the weather service"));
            Map<String, Object> cur = Json.asMap(w.get("current"));
            Map<String, Object> daily = Json.asMap(w.get("daily"));
            String now = String.format("%s: %s, %.0f°C (feels like %.0f°C), humidity %.0f%%, wind %.0f km/h.", name,
                    describe(num(cur, "weather_code")), num(cur, "temperature_2m"), num(cur, "apparent_temperature"),
                    num(cur, "relative_humidity_2m"), num(cur, "wind_speed_10m"));
            StringBuilder sb = new StringBuilder(now);
            String[] labels = { "Today", "Tomorrow" };
            for (int i = 0; i < 2; i++) {
                sb.append(String.format(" %s: %s, %.0f–%.0f°C, %.0f%% chance of rain.", labels[i],
                        describe(at(daily, "weather_code", i)), at(daily, "temperature_2m_min", i),
                        at(daily, "temperature_2m_max", i), at(daily, "precipitation_probability_max", i)));
            }
            return ToolResult.ok(sb.toString());
        }

        /** Cities better known by their old names; the weather service only knows the official ones. */
        private static final Map<String, String> RENAMED = Map.ofEntries(
                Map.entry("bangalore", "Bengaluru"), Map.entry("bombay", "Mumbai"), Map.entry("madras", "Chennai"),
                Map.entry("calcutta", "Kolkata"), Map.entry("mysore", "Mysuru"), Map.entry("mangalore", "Mangaluru"),
                Map.entry("gurgaon", "Gurugram"), Map.entry("trivandrum", "Thiruvananthapuram"), Map.entry("poona", "Pune"),
                Map.entry("baroda", "Vadodara"), Map.entry("cochin", "Kochi"), Map.entry("belgaum", "Belagavi"),
                Map.entry("hubli", "Hubballi"), Map.entry("shimoga", "Shivamogga"), Map.entry("tumkur", "Tumakuru"),
                Map.entry("gulbarga", "Kalaburagi"), Map.entry("bellary", "Ballari"), Map.entry("bijapur", "Vijayapura"),
                Map.entry("pondicherry", "Puducherry"), Map.entry("benares", "Varanasi"), Map.entry("allahabad", "Prayagraj"));

        /** The user's country code, from the PC's time zone (India) or language settings. */
        static String homeCountry(ToolContext ctx) {
            String zone = ctx.zone().getId();
            if (zone.equals("Asia/Kolkata") || zone.equals("Asia/Calcutta")) {
                return "IN";
            }
            String c = java.util.Locale.getDefault().getCountry();
            return c.isEmpty() ? "IN" : c;
        }

        private static double num(Map<String, Object> m, String key) {
            return m.get(key) instanceof Number n ? n.doubleValue() : 0;
        }

        private static double at(Map<String, Object> m, String key, int i) {
            List<Object> l = Json.asList(m.get(key));
            return i < l.size() && l.get(i) instanceof Number n ? n.doubleValue() : 0;
        }

        /** WMO weather codes used by Open-Meteo. */
        static String describe(double code) {
            int c = (int) code;
            if (c == 0) return "clear sky";
            if (c <= 2) return "partly cloudy";
            if (c == 3) return "overcast";
            if (c <= 48) return "foggy";
            if (c <= 57) return "drizzle";
            if (c <= 67) return "rain";
            if (c <= 77) return "snow";
            if (c <= 82) return "rain showers";
            if (c <= 86) return "snow showers";
            return "thunderstorms";
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Check the weather in " + args.string("city");
        }
    }

    public static class News extends Tool {
        private static final Pattern ITEM = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
        private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
        private static final Pattern SOURCE = Pattern.compile("<source[^>]*>(.*?)</source>", Pattern.DOTALL);

        public News() {
            super("get_news", "info", "Get the latest news headlines, optionally about a topic.", RiskLevel.NETWORK_READ,
                    ToolParam.optional("topic", STRING, "Topic to filter by; omit for top headlines"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            String topic = args.string("topic", null);
            List<String> lines = headlines(topic, 6);
            if (lines.isEmpty()) {
                return ToolResult.ok("No headlines found" + (topic != null ? " about " + topic : "") + " in the last 2 days.");
            }
            return ToolResult.ok("Latest headlines" + (topic != null ? " about " + topic : "") + " (Google News):\n"
                    + String.join("\n", lines));
        }

        /** Newest headlines from Google News (India edition), each with its source and age. */
        static List<String> headlines(String topic, int max) throws ToolException {
            String region = "hl=en-IN&gl=IN&ceid=IN:en";
            // Topic searches are limited to the last 2 days so old stories are never presented as news.
            String url = topic != null
                    ? "https://news.google.com/rss/search?q=" + enc(topic + " when:2d") + "&" + region
                    : "https://news.google.com/rss?" + region;
            String xml = get(url, "Google News");
            record Item(String title, String source, ZonedDateTime published) {
            }
            List<Item> items = new ArrayList<>();
            Matcher m = ITEM.matcher(xml);
            while (m.find() && items.size() < 40) {
                Matcher t = TITLE.matcher(m.group(1));
                if (!t.find()) {
                    continue;
                }
                Matcher src = SOURCE.matcher(m.group(1));
                Matcher date = PUB_DATE.matcher(m.group(1));
                String title = unescape(t.group(1).replaceAll("<!\\[CDATA\\[|]]>", ""));
                String source = src.find() ? unescape(src.group(1)) : "";
                if (!source.isEmpty() && title.endsWith(" - " + source)) {
                    title = title.substring(0, title.length() - source.length() - 3);
                }
                ZonedDateTime when = null;
                if (date.find()) {
                    try {
                        when = ZonedDateTime.parse(date.group(1).trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
                    } catch (DateTimeParseException ignored) {
                        // keep the headline without a time
                    }
                }
                items.add(new Item(title, source, when));
            }
            // Newest first.
            items.sort((a, b) -> b.published() == null ? -1 : a.published() == null ? 1 : b.published().compareTo(a.published()));
            List<String> lines = new ArrayList<>();
            ZonedDateTime now = ZonedDateTime.now();
            for (Item it : items.subList(0, Math.min(max, items.size()))) {
                String age = it.published() == null ? "" : ", " + age(Duration.between(it.published(), now));
                lines.add((lines.size() + 1) + ". " + it.title() + " (" + (it.source().isEmpty() ? "news" : it.source()) + age + ")");
            }
            return lines;
        }

        private static final Pattern PUB_DATE = Pattern.compile("<pubDate>(.*?)</pubDate>", Pattern.DOTALL);

        private static String age(Duration d) {
            long min = Math.max(0, d.toMinutes());
            if (min < 60) {
                return min + " min ago";
            }
            long h = min / 60;
            return h < 48 ? h + " h ago" : (h / 24) + " days ago";
        }

        static String unescape(String s) {
            return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                    .replace("&lt;", "<").replace("&gt;", ">").trim();
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Get news" + (args.has("topic") ? " about " + args.string("topic") : " headlines");
        }
    }

    /** Live cricket scores from ESPNcricinfo plus the latest cricket headlines. */
    public static class Cricket extends Tool {
        private static final Pattern ITEM = Pattern.compile("<item>.*?<title>(.*?)</title>", Pattern.DOTALL);

        public Cricket() {
            super("get_cricket", "info",
                    "Get live and recent cricket scores (ESPNcricinfo) and the latest cricket news, optionally for one team.",
                    RiskLevel.NETWORK_READ, ToolParam.optional("team", STRING, "Team or country, e.g. India"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            String team = args.string("team", null);
            String xml = get("https://static.cricinfo.com/rss/livescores.xml", "ESPNcricinfo");
            List<String> scores = new ArrayList<>();
            Matcher m = ITEM.matcher(xml);
            while (m.find()) {
                String line = News.unescape(m.group(1)).replaceAll("\\s+", " ").trim();
                if (team == null || line.toLowerCase().contains(team.toLowerCase())) {
                    scores.add("- " + line.replace(" *", " (batting)"));
                }
            }
            StringBuilder sb = new StringBuilder();
            if (scores.isEmpty()) {
                sb.append(team == null ? "No matches are live right now on ESPNcricinfo."
                        : "No live or recent " + team + " match on ESPNcricinfo right now.");
            } else {
                sb.append("Live and recent scores (ESPNcricinfo):\n")
                        .append(String.join("\n", scores.subList(0, Math.min(8, scores.size()))));
            }
            List<String> news = News.headlines(team == null ? "cricket" : team + " cricket", 5);
            if (!news.isEmpty()) {
                sb.append("\n\nLatest cricket news (Google News):\n").append(String.join("\n", news));
            }
            return ToolResult.ok(sb.toString());
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Get cricket scores and news" + (args.has("team") ? " for " + args.string("team") : "");
        }
    }
}
