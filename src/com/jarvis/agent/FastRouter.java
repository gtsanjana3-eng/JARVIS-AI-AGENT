package com.jarvis.agent;

import com.jarvis.agent.workflow.AnswerWorkflow;
import com.jarvis.agent.workflow.AppTarget;
import com.jarvis.agent.workflow.ResearchRequest;
import com.jarvis.agent.workflow.ResearchWorkflow;
import com.jarvis.agent.workflow.Workflow;
import com.jarvis.agent.workflow.WriteWorkflow;
import com.jarvis.ai.ToolCall;
import com.jarvis.system.AppCatalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises clear, direct commands ("open WhatsApp", "play lofi on YouTube",
 * "remind me to drink water in 10 minutes") and maps them straight to a tool
 * call, without waiting for the language model. This makes common commands
 * respond in about a second and removes any chance of the model "pretending".
 * Anything that is not a clear command goes to the full AI agent.
 */
final class FastRouter {

    /** A recognised command: either one tool call, or a multi-step {@link Workflow}. */
    record Route(String intent, ToolCall call, Workflow workflow) {
        Route(String intent, ToolCall call) {
            this(intent, call, null);
        }

        static Route of(Workflow w) {
            return new Route(w.intent(), null, w);
        }
    }

    private static final String TARGET = "(word|ms word|microsoft word|a word (?:document|file)|word (?:document|file)|"
            + "notepad|a text file|text file|(?:google )?chrome|edge|(?:the |a )?browser|(?:a )?web ?page|vs ?code|"
            + "visual studio code|(?:a )?document|a doc)";
    private static final Pattern RESEARCH = p("^(?:research|do (?:a |some )?research (?:on|about)|find (?:out )?"
            + "(?:information|info|details|facts) (?:about|on)|look up (?:information|info) (?:about|on)|"
            + "(?:make|prepare|create|write|give me) (?:a |me a )?(?:detailed |full |short )?(?:report|summary) (?:on|about)|"
            + "(?:search|google)(?: about| for)?)\\s+(.+)$");
    private static final Pattern WRITE = p("^(?:write|draft|type|prepare|create|make|compose)\\s+(?:me\\s+)?"
            + "(?:a|an|some|the|my)?\\s*([a-z ]{3,30}?)\\s+(?:about|on|for|regarding|to|asking)\\s+(.+?)\\s+"
            + "(?:in|into|on|using|with)\\s+" + TARGET + "$");
    private static final Pattern WRITE_SHORT = p("^(?:write|draft|type|prepare|create|make|compose)\\s+(?:me\\s+)?"
            + "(?:a|an|some|the|my)?\\s*([a-z ]{3,30}?)\\s+(?:in|into|using)\\s+" + TARGET + "$");
    private static final Pattern WEATHER = p("^(?:what(?:'s| is) the |how(?:'s| is) the )?weather(?: like| forecast)?"
            + "(?: today| now| tomorrow)?\\s+(?:in|at|for)\\s+(.+?)(?: today| now| tomorrow)?$|^will it rain\\s+(?:in|at)\\s+"
            + "(.+?)(?: today| tomorrow)?$");
    private static final Pattern SCREENSHOT = p("^(?:take|capture|grab|get)\\s+(?:a\\s+)?screen\\s?shot.*$|^screen\\s?shot$");
    private static final Pattern LOCK = p("^lock\\s+(?:my\\s+|the\\s+)?(?:computer|pc|laptop|screen|system|device)$");

    private static final Pattern FILLER = Pattern.compile(
            "^(?:(?:hey|hi|hello|ok|okay)[,!.]?\\s+)?(?:jarvis[,!.]?\\s*)?(please |can you |could you |would you |will you |i want you to |i want to )*",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern YOUTUBE = p("^(?:play|search(?: for)?|find|show(?: me)?|watch)\\s+(.+?)\\s+(?:on|in|from)\\s+youtube$|^youtube\\s+(.+)$|^(?:open\\s+)?youtube\\s+and\\s+(?:play|search(?: for)?)\\s+(.+)$");
    private static final Pattern SPOTIFY = p("^(?:play|search(?: for)?)\\s+(.+?)\\s+(?:on|in)\\s+spotify$");
    private static final Pattern GOOGLE = p("^(?:open google and (?:start )?search(?:ing)?(?: for)?|google|search(?: google)?(?: for)?|look up)\\s+(.+?)(?:\\s+(?:on|in|using) google)?$");
    private static final Pattern REMEMBER = p("^remember (?:that )?(.{3,200})$");
    private static final Pattern RECALL = p("^(?:what do you (?:know|remember) about me|what do you remember|show (?:me )?(?:my )?memories)$");
    private static final Pattern WHATSAPP = p("^(?:send|message|text|whatsapp)\\s+(?:a\\s+)?(?:message\\s+)?[\"']?(.+?)[\"']?\\s+to\\s+(.+?)(?:\\s+(?:on|in|via|through)\\s+whatsapp)?$");
    private static final Pattern WHATSAPP2 = p("^(?:whatsapp|message|text)\\s+(.+?)\\s+(?:saying|that)\\s+(.+)$");
    private static final Pattern EMAIL = p("^(?:send\\s+(?:an?\\s+)?)?e-?mail\\s+(?:to\\s+)?(\\S+@\\S+)(?:\\s+(?:about|saying|that|with subject)\\s+(.+))?$");
    private static final Pattern DIRECTIONS = p("^(?:get\\s+)?(?:directions|navigate|route|take me|how do i get)\\s+(?:me\\s+)?to\\s+(.+)$");
    private static final Pattern SETTINGS = p("^(?:open|show|go to)\\s+(?:the\\s+|my\\s+)?(wi-?fi|bluetooth|display|sound|battery|update|windows update|apps|notifications?|personali[sz]ation|privacy|storage|network)\\s+settings$");
    private static final Pattern OPEN = p("^(?:open|launch|start|run|bring up)\\s+(?:the\\s+|my\\s+|up\\s+)?(.+?)(?:\\s+app(?:lication)?)?$");
    private static final Pattern SITE = p("^(?:[a-z0-9-]+\\.)+(?:com|org|net|in|edu|io|gov|co|ai|dev)(?:/\\S*)?$");
    private static final Pattern TIME = p("^(?:what(?:'s| is) the (?:time|date)(?: now| today)?|what time is it(?: now)?|what(?:'s| is) today'?s date|what day is (?:it|today))$");
    private static final Pattern MATH = p("^(?:what(?:'s| is)\\s+|calculate\\s+|compute\\s+)?([0-9][0-9+\\-*/^%.() x×÷]*[0-9)])$");
    private static final Pattern REMIND1 = p("^remind me (?:to |about )?(.+?)\\s+((?:in|at|after|on|tomorrow|today|tonight)\\b.+)$");
    private static final Pattern REMIND2 = p("^remind me ((?:in|at|after|tomorrow|today)\\b.+?)\\s+(?:to|about)\\s+(.+)$");

    private static final Map<String, String> SITES = new LinkedHashMap<>();

    static {
        SITES.put("youtube", "https://www.youtube.com");
        SITES.put("google", "https://www.google.com");
        SITES.put("gmail", "https://mail.google.com");
        SITES.put("github", "https://github.com");
        SITES.put("instagram", "https://www.instagram.com");
        SITES.put("facebook", "https://www.facebook.com");
        SITES.put("linkedin", "https://www.linkedin.com");
        SITES.put("wikipedia", "https://www.wikipedia.org");
        SITES.put("chatgpt", "https://chatgpt.com");
        SITES.put("netflix", "https://www.netflix.com");
        SITES.put("amazon", "https://www.amazon.in");
        SITES.put("google maps", "https://maps.google.com");
    }

    private FastRouter() {
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private static ToolCall call(String tool, Object... kv) {
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            args.put((String) kv[i], kv[i + 1]);
        }
        return new ToolCall(tool, args);
    }

    static Optional<Route> route(String request, AppCatalog apps) {
        String t = FILLER.matcher(request.trim()).replaceFirst("").replaceAll("[.!?]+$", "").trim();
        if (t.isEmpty() || t.length() > 200) {
            return Optional.empty();
        }
        Matcher m;
        // ---- multi-step tasks (workflows)
        if ((m = RESEARCH.matcher(t)).matches()) {
            boolean plainSearch = t.matches("(?i)^(?:search|google)\\b.*");
            ResearchRequest r = ResearchRequest.parse(m.group(1), t);
            // "search X" alone is a Google search; "search X and put it in Word" is research.
            if ((!plainSearch || r.target() != null) && !r.topic().isEmpty()
                    && !r.topic().matches("(?i)(yourself|you|me|myself|it|this|that)")) {
                return Optional.of(Route.of(new ResearchWorkflow(r)));
            }
        }
        if ((m = WRITE.matcher(t)).matches()) {
            Optional<AppTarget> target = AppTarget.parse(m.group(3));
            if (target.isPresent()) {
                return Optional.of(Route.of(new WriteWorkflow(m.group(1).trim(), m.group(2).trim(), target.get())));
            }
        }
        if ((m = WRITE_SHORT.matcher(t)).matches()) {
            Optional<AppTarget> target = AppTarget.parse(m.group(2));
            if (target.isPresent()) {
                return Optional.of(Route.of(new WriteWorkflow(m.group(1).trim(), m.group(1).trim(), target.get())));
            }
        }
        // ---- single actions
        if ((m = WEATHER.matcher(t)).matches()) {
            return Optional.of(new Route("check the weather", call("get_weather", "city", first(m))));
        }
        // ---- cricket: live scores from ESPNcricinfo + cricket headlines
        // (a history question like "who won the 2011 cricket world cup" is answered from sources instead)
        boolean liveCricket = t.matches("(?i).*\\b(score|scores|scorecard|live|news|updates?|today|now|latest|current|"
                + "going on|happening)\\b.*") || t.matches("(?i)^(?:cricket|ipl)$");
        if ((t.matches("(?i).*\\b(cricket|ipl|test match|odi|t20)\\b.*") && (liveCricket || !FACT_QUESTION.matcher(t).matches()))
                || t.matches("(?i).*\\b(live score|match score|scorecard)s?\\b.*")) {
            String team = team(t);
            return Optional.of(new Route("cricket scores and news",
                    team == null ? call("get_cricket") : call("get_cricket", "team", team)));
        }
        // ---- news (Google News), with the topic cleaned of filler words
        if (t.matches("(?i).*\\b(news|headlines?)\\b.*") && t.split("\\s+").length <= 12) {
            String topic = newsTopic(t);
            return Optional.of(new Route("get the news", topic == null ? call("get_news") : call("get_news", "topic", topic)));
        }
        if (SCREENSHOT.matcher(t).matches()) {
            return Optional.of(new Route("take a screenshot", call("take_screenshot")));
        }
        if (t.matches("(?i)^(?:start jarvis (?:automatically )?(?:with|when|on) windows(?: starts)?|(?:enable|turn on) "
                + "auto ?start|run (?:jarvis )?in (?:the )?background|start (?:jarvis )?automatically)$")) {
            return Optional.of(new Route("start with Windows", call("autostart", "action", "on")));
        }
        if (t.matches("(?i)^(?:stop starting (?:jarvis )?with windows|(?:disable|turn off) auto ?start|"
                + "don'?t start (?:jarvis )?automatically)$")) {
            return Optional.of(new Route("stop starting with Windows", call("autostart", "action", "off")));
        }
        if (LOCK.matcher(t).matches()) {
            return Optional.of(new Route("lock the computer", call("lock_screen")));
        }
        if ((m = REMEMBER.matcher(t)).matches()) {
            return Optional.of(new Route("remember a fact", call("remember_fact", "fact", cap(m.group(1)), "category", "personal")));
        }
        if (RECALL.matcher(t).matches()) {
            return Optional.of(new Route("recall memories", call("recall_memories")));
        }
        if ((m = TIME.matcher(t)).matches()) {
            return Optional.of(new Route("tell the time", call("get_datetime")));
        }
        // "45 into 800" (Indian English for 45 × 800), "2 times 3", "10 divided by 4"...
        String mt = t.replaceAll("(?i)^(?:what is the answer (?:of|to)|answer of|multiplication of)\\s+", "")
                .replaceAll("(?i)\\s+(?:into|times|multiplied by|and)\\s+(?=\\d)", " * ")
                .replaceAll("(?i)\\s+divided by\\s+", " / ").replaceAll("(?i)\\s+plus\\s+", " + ")
                .replaceAll("(?i)\\s+minus\\s+", " - ");
        if ((m = MATH.matcher(mt)).matches() && mt.matches(".*\\d\\s*[-+*/^%x×÷]\\s*\\(?\\d.*")) {
            return Optional.of(new Route("calculate", call("calculator", "expression", m.group(1).replace('x', '*'))));
        }
        if ((m = REMIND1.matcher(t)).matches()) {
            return Optional.of(new Route("set a reminder", call("create_reminder", "text", cap(m.group(1)), "when", m.group(2))));
        }
        if ((m = REMIND2.matcher(t)).matches()) {
            return Optional.of(new Route("set a reminder", call("create_reminder", "text", cap(m.group(2)), "when", m.group(1))));
        }
        if ((m = YOUTUBE.matcher(t)).matches()) {
            String q = first(m);
            return Optional.of(new Route("search YouTube", call("app_action", "action", "youtube_search", "query", q)));
        }
        if ((m = SPOTIFY.matcher(t)).matches()) {
            return Optional.of(new Route("search Spotify", call("app_action", "action", "spotify_search", "query", m.group(1))));
        }
        if ((m = SETTINGS.matcher(t)).matches()) {
            return Optional.of(new Route("open Windows settings", call("app_action", "action", "windows_settings", "page", m.group(1))));
        }
        if ((m = DIRECTIONS.matcher(t)).matches()) {
            return Optional.of(new Route("get directions", call("app_action", "action", "maps_directions", "destination", m.group(1))));
        }
        if ((m = EMAIL.matcher(t)).matches()) {
            String about = m.group(2) == null ? "" : m.group(2);
            return Optional.of(new Route("prepare an email", call("app_action", "action", "compose_email",
                    "to", m.group(1), "subject", about, "body", "")));
        }
        if ((m = WHATSAPP.matcher(t)).matches() && (t.toLowerCase().contains("whatsapp") || t.toLowerCase().startsWith("send"))) {
            return Optional.of(whatsapp(m.group(1), m.group(2)));
        }
        if ((m = WHATSAPP2.matcher(t)).matches()) {
            return Optional.of(whatsapp(m.group(2), m.group(1)));
        }
        if ((m = OPEN.matcher(t)).matches()) {
            String target = m.group(1).trim();
            if (SITE.matcher(target).matches()) {
                return Optional.of(new Route("open a website", call("open_website", "url", target)));
            }
            if (apps.find(target) != null) {
                return Optional.of(new Route("open " + target, call("open_application", "app", target)));
            }
            String site = SITES.get(target.toLowerCase());
            if (site != null) {
                return Optional.of(new Route("open " + target, call("open_website", "url", site)));
            }
        }
        // ---- factual questions: answered from real sources, not from the AI's memory
        if (FACT_QUESTION.matcher(t).matches() && !t.matches("(?i).*\\b(you|your|yourself|me|my|mine|i|i'm|we|our)\\b.*")) {
            return Optional.of(Route.of(new AnswerWorkflow(t)));
        }
        if ((m = GOOGLE.matcher(t)).matches() && !t.toLowerCase().startsWith("search my") && !t.toLowerCase().contains("notes")) {
            return Optional.of(new Route("search Google", call("app_action", "action", "google_search", "query", m.group(1))));
        }
        return Optional.empty();
    }

    private static Route whatsapp(String text, String recipient) {
        String digits = recipient.replaceAll("[^0-9]", "");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("action", "whatsapp_message");
        args.put("text", text);
        if (digits.length() >= 10) {
            args.put("phone", digits);
        }
        args.put("recipient", recipient);
        return new Route("prepare a WhatsApp message to " + recipient, new ToolCall("app_action", args));
    }

    private static final Pattern FACT_QUESTION = p("^(?:(?:who|what|when|where|which|why)(?:'s|\\s)|how (?:many|much|old|tall|"
            + "big|far|long|does|did|do|is|are|was|were)\\b|(?:tell me about|information about|details about|facts about|"
            + "give me (?:information|info|details|facts) (?:about|on)|is it true that)\\s).{6,}$");
    private static final List<String> TEAMS = List.of("india", "australia", "england", "pakistan", "south africa",
            "new zealand", "sri lanka", "bangladesh", "afghanistan", "west indies", "zimbabwe", "ireland", "netherlands",
            "nepal", "scotland", "uae", "usa");

    private static String team(String t) {
        String low = t.toLowerCase();
        for (String team : TEAMS) {
            if (low.matches(".*\\b" + team + "\\b.*")) {
                return Character.toUpperCase(team.charAt(0)) + team.substring(1);
            }
        }
        return null;
    }

    /** "Give me updates about the latest news" → no topic; "news about ISRO launch" → "ISRO launch". */
    private static String newsTopic(String t) {
        String topic = t.replaceAll("(?i)\\b(give|tell|show|read|get|me|the|latest|today'?s|today|top|current|recent|some|"
                + "any|updates?|news|headlines?|about|on|for|regarding|of|please|what'?s|what|is|are|happening|now|"
                + "breaking|all|new|a|an|and|in|from|jarvis)\\b", " ").replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ").trim();
        return topic.isEmpty() ? null : topic;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String first(Matcher m) {
        for (int i = 1; i <= m.groupCount(); i++) {
            if (m.group(i) != null) {
                return m.group(i).trim();
            }
        }
        return "";
    }
}
