package com.jarvis.tools.builtin;

import com.jarvis.system.AppCatalog;
import com.jarvis.system.WindowFocus;
import com.jarvis.tools.RiskLevel;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolArgs;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolParam;
import com.jarvis.tools.ToolResult;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.jarvis.tools.ToolParam.Type.STRING;

/**
 * Actions on this computer: open any installed app, open documents, websites
 * and app shortcuts (YouTube, WhatsApp, email, Maps...). All are EXTERNAL, so
 * the user approves every launch. Opened windows are brought to the front.
 * JARVIS never sends a message or email itself: it prepares it and the user
 * presses Send.
 */
public final class DesktopTools {

    private DesktopTools() {
    }

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Opens a URI with whatever app Windows has registered for it, then brings that app forward. */
    static void openUri(String uri, String focusHint) throws ToolException {
        try {
            if (uri.matches("(?i)^https?://.*") && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(uri));
            } else if (isWindows()) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri).start();
            } else {
                throw new ToolException("Opening links is not supported on this system.");
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new ToolException("Could not open " + uri + ": " + e.getMessage());
        }
        WindowFocus.bringToFrontAsync(focusHint, 0);
    }

    /** Window titles usually end with the short product name ("Document1 - Word"). */
    static String focusHint(String appName) {
        return appName.replaceFirst("(?i)^microsoft\\s+", "").replaceFirst("(?i)^google\\s+", "Google ");
    }

    // ================================================================== open any installed app

    public static class OpenApplication extends Tool {
        public OpenApplication() {
            super("open_application", "desktop",
                    "Start any application installed on this PC by name (e.g. Notepad, Chrome, Word, Excel, Spotify, "
                            + "WhatsApp, VS Code, Calculator, Settings) and bring it to the front.",
                    RiskLevel.EXTERNAL,
                    ToolParam.required("app", STRING, "The app's name as the user said it"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            if (!isWindows()) {
                throw new ToolException("Launching applications is only supported on Windows.");
            }
            String wanted = args.string("app");
            AppCatalog.App app = ctx.apps().find(wanted);
            if (app == null) {
                List<String> alt = ctx.apps().suggestions(wanted, 6);
                throw new ToolException("No installed app matches '" + wanted + "'."
                        + (alt.isEmpty() ? " Use list_applications to see what is installed."
                        : " Similar apps: " + String.join(", ", alt) + "."));
            }
            long pid = 0;
            try {
                switch (app.kind()) {
                    case EXECUTABLE -> pid = new ProcessBuilder(app.target()).start().pid();
                    case SHORTCUT -> new ProcessBuilder("cmd", "/c", "start", "", app.target()).start();
                    case STORE_APP -> new ProcessBuilder("explorer.exe", "shell:AppsFolder\\" + app.target()).start();
                }
            } catch (IOException e) {
                throw new ToolException("Could not start " + app.name() + ": " + e.getMessage());
            }
            WindowFocus.bringToFrontAsync(focusHint(app.name()), pid);
            return ToolResult.ok("Started " + app.name() + " and brought it to the front.");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Open " + args.string("app");
        }
    }

    public static class ListApplications extends Tool {
        public ListApplications() {
            super("list_applications", "desktop", "List installed applications, optionally filtered by a word.",
                    RiskLevel.SAFE, ToolParam.optional("query", STRING, "Word to filter by, e.g. 'adobe'"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) {
            List<String> names = args.has("query") ? ctx.apps().suggestions(args.string("query"), 40)
                    : ctx.apps().names();
            if (names.isEmpty()) {
                return ToolResult.ok("No installed apps match '" + args.string("query", "") + "'.");
            }
            int total = names.size();
            List<String> shown = names.subList(0, Math.min(80, total));
            return ToolResult.ok(total + " app(s): " + String.join(", ", shown) + (total > shown.size() ? ", …" : ""));
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "List installed apps" + (args.has("query") ? " matching '" + args.string("query") + "'" : "");
        }
    }

    // ================================================================== documents and websites

    public static class OpenFile extends Tool {
        public OpenFile() {
            super("open_file", "desktop",
                    "Open a document from the workspace in its normal app (e.g. a .txt in Notepad, a .docx in Word). "
                            + "Create it first with write_document if needed.",
                    RiskLevel.EXTERNAL, ToolParam.required("path", STRING, "File path inside the workspace"),
                    ToolParam.choice("app", false, "Which app to open it with (default: the usual app)",
                            "default", "notepad", "word", "browser", "vscode"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            Path f = ctx.workspace().resolve(args.string("path"));
            if (!Files.isRegularFile(f)) {
                throw new ToolException("File not found: " + ctx.workspace().display(f));
            }
            String app = args.string("app", "default");
            String label;
            try {
                switch (app) {
                    case "notepad" -> {
                        long pid = new ProcessBuilder("notepad.exe", f.toString()).start().pid();
                        WindowFocus.bringToFrontAsync(f.getFileName().toString(), pid);
                        label = "Notepad";
                    }
                    case "word", "vscode" -> {
                        String name = app.equals("word") ? "Word" : "Visual Studio Code";
                        AppCatalog.App found = ctx.apps().find(name);
                        if (found == null || found.kind() != AppCatalog.App.Kind.SHORTCUT) {
                            throw new ToolException(name + " is not installed on this PC.");
                        }
                        new ProcessBuilder("cmd", "/c", "start", "", found.target(), f.toString()).start();
                        WindowFocus.bringToFrontAsync(app.equals("word") ? "Word" : "Visual Studio Code", 0);
                        label = name;
                    }
                    case "browser" -> {
                        Desktop.getDesktop().browse(f.toUri());
                        label = "your browser";
                    }
                    default -> {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                            Desktop.getDesktop().open(f.toFile());
                        } else {
                            new ProcessBuilder("cmd", "/c", "start", "", f.toString()).start();
                        }
                        WindowFocus.bringToFrontAsync(f.getFileName().toString(), 0);
                        label = "its default app";
                    }
                }
            } catch (IOException | UnsupportedOperationException e) {
                throw new ToolException("Could not open " + ctx.workspace().display(f) + ": " + e.getMessage());
            }
            return ToolResult.ok("Opened " + ctx.workspace().display(f) + " in " + label + ".");
        }

        @Override
        public String describeCall(ToolArgs args) {
            String app = args.string("app", "default");
            return "Open workspace/" + args.string("path") + (app.equals("default") ? "" : " in " + switch (app) {
                case "word" -> "Word";
                case "vscode" -> "VS Code";
                case "notepad" -> "Notepad";
                default -> "your browser";
            });
        }
    }

    public static class OpenWebsite extends Tool {
        public OpenWebsite() {
            super("open_website", "desktop", "Open a web page in the user's default browser.", RiskLevel.EXTERNAL,
                    ToolParam.required("url", STRING, "Full address starting with https://"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            String raw = args.string("url");
            String url = raw.matches("(?i)^https?://.*") ? raw : "https://" + raw;
            URI uri;
            try {
                uri = URI.create(url);
            } catch (IllegalArgumentException e) {
                throw new ToolException("'" + raw + "' is not a valid web address.");
            }
            if (uri.getHost() == null || !uri.getHost().contains(".")) {
                throw new ToolException("'" + raw + "' is not a valid web address.");
            }
            openUri(uri.toString(), "");
            return ToolResult.ok("Opened " + uri + " in the default browser.");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Open " + args.string("url") + " in your browser";
        }
    }

    // ================================================================== screen and session

    public static class Screenshot extends Tool {
        public Screenshot() {
            super("take_screenshot", "desktop", "Capture the whole screen and save it as a PNG in workspace/screenshots.",
                    RiskLevel.SAFE);
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            java.awt.Rectangle area = new java.awt.Rectangle();
            for (java.awt.GraphicsDevice d : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                area = area.union(d.getDefaultConfiguration().getBounds());
            }
            java.awt.image.BufferedImage img = new java.awt.Robot().createScreenCapture(area);
            Path dir = ctx.workspace().resolve("screenshots");
            Files.createDirectories(dir);
            String name = "screenshot-" + java.time.LocalDateTime.now(ctx.zone())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png";
            Path file = dir.resolve(name);
            javax.imageio.ImageIO.write(img, "png", file.toFile());
            return ToolResult.ok("Screenshot saved to " + ctx.workspace().display(file) + " (" + img.getWidth() + "x"
                    + img.getHeight() + "). PATH=screenshots/" + name);
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Take a screenshot";
        }
    }

    public static class LockScreen extends Tool {
        public LockScreen() {
            super("lock_screen", "desktop", "Lock the computer (Windows lock screen).", RiskLevel.EXTERNAL);
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            if (!isWindows()) {
                throw new ToolException("Locking is only supported on Windows.");
            }
            new ProcessBuilder("rundll32.exe", "user32.dll,LockWorkStation").start();
            return ToolResult.ok("The computer is locking now.");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Lock the computer";
        }
    }

    // ================================================================== tasks inside apps

    public static class AppAction extends Tool {
        private static final Map<String, String> SETTINGS = Map.ofEntries(
                Map.entry("wifi", "network-wifi"), Map.entry("network", "network-status"),
                Map.entry("bluetooth", "bluetooth"), Map.entry("display", "display"), Map.entry("sound", "sound"),
                Map.entry("battery", "batterysaver"), Map.entry("update", "windowsupdate"),
                Map.entry("apps", "appsfeatures"), Map.entry("notifications", "notifications"),
                Map.entry("personalization", "personalization"), Map.entry("privacy", "privacy"),
                Map.entry("storage", "storagesense"), Map.entry("home", ""));

        public AppAction() {
            super("app_action", "desktop",
                    "Do something inside another app, opened in front of JARVIS: youtube_search (query), "
                            + "google_search (query), spotify_search (query), maps_directions (destination), "
                            + "compose_email (to, subject, body), whatsapp_message (text, optional phone with country "
                            + "code), windows_settings (page: wifi, bluetooth, display, sound, battery, update, apps, "
                            + "notifications, personalization, privacy, storage). Emails and messages are only "
                            + "prepared: the user presses Send.",
                    RiskLevel.EXTERNAL,
                    ToolParam.choice("action", true, "What to do", "youtube_search", "google_search",
                            "spotify_search", "maps_directions", "compose_email", "whatsapp_message", "windows_settings"),
                    ToolParam.optional("query", STRING, "Search words (searches)"),
                    ToolParam.optional("destination", STRING, "Place to get directions to"),
                    ToolParam.optional("to", STRING, "Email address (compose_email)"),
                    ToolParam.optional("subject", STRING, "Email subject"),
                    ToolParam.optional("body", STRING, "Email body"),
                    ToolParam.optional("text", STRING, "Message text (whatsapp_message)"),
                    ToolParam.optional("phone", STRING, "WhatsApp number with country code, digits only"),
                    ToolParam.optional("recipient", STRING, "Contact name, if no number is known (the user picks the chat)"),
                    ToolParam.optional("page", STRING, "Settings page (windows_settings)"));
        }

        private static String need(ToolArgs args, String key, String action) throws ToolException {
            if (!args.has(key)) {
                throw new ToolException(action + " needs '" + key + "'.");
            }
            return args.string(key);
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            String action = args.string("action");
            switch (action) {
                case "youtube_search" -> {
                    String q = need(args, "query", action);
                    openUri("https://www.youtube.com/results?search_query=" + enc(q), "");
                    return ToolResult.ok("Opened YouTube results for \"" + q + "\". The user can pick a video to play.");
                }
                case "google_search" -> {
                    String q = need(args, "query", action);
                    openUri("https://www.google.com/search?q=" + enc(q), "");
                    return ToolResult.ok("Opened Google results for \"" + q + "\".");
                }
                case "spotify_search" -> {
                    String q = need(args, "query", action);
                    if (ctx.apps().find("Spotify") != null) {
                        openUri("spotify:search:" + enc(q), "Spotify");
                        return ToolResult.ok("Opened Spotify search for \"" + q + "\" in the Spotify app.");
                    }
                    openUri("https://open.spotify.com/search/" + enc(q), "");
                    return ToolResult.ok("Spotify app is not installed, so opened Spotify Web search for \"" + q + "\".");
                }
                case "maps_directions" -> {
                    String d = need(args, "destination", action);
                    openUri("https://www.google.com/maps/dir/?api=1&destination=" + enc(d), "");
                    return ToolResult.ok("Opened Google Maps directions to " + d + ".");
                }
                case "compose_email" -> {
                    String to = args.string("to", "");
                    if (!to.isEmpty() && !to.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                        throw new ToolException("'" + to + "' is not a valid email address.");
                    }
                    String uri = "mailto:" + to + "?subject=" + enc(args.string("subject", ""))
                            + "&body=" + enc(args.string("body", ""));
                    openUri(uri, "Mail");
                    return ToolResult.ok("Opened a new email draft" + (to.isEmpty() ? "" : " to " + to)
                            + " in the default mail app. It has NOT been sent: the user must review it and press Send.");
                }
                case "whatsapp_message" -> {
                    String text = need(args, "text", action);
                    String phone = args.string("phone", "").replaceAll("[^0-9]", "");
                    String query = "text=" + enc(text) + (phone.isEmpty() ? "" : "&phone=" + phone);
                    if (ctx.apps().find("WhatsApp") != null) {
                        openUri("whatsapp://send?" + query, "WhatsApp");
                    } else {
                        openUri("https://web.whatsapp.com/send?" + query, "");
                    }
                    String who = args.string("recipient", "");
                    return ToolResult.ok("Opened WhatsApp with \"" + text + "\" ready" + (phone.isEmpty()
                            ? (who.isEmpty() ? ". Choose the chat" : ". Choose " + who + "'s chat") : " in the chat with +" + phone)
                            + " and press Send. I can't pick contacts or send messages myself, so it has NOT been sent yet.");
                }
                case "windows_settings" -> {
                    String page = need(args, "page", action).toLowerCase().replaceAll("[^a-z]", "");
                    String key = SETTINGS.keySet().stream().filter(page::contains).findFirst()
                            .orElseThrow(() -> new ToolException("Unknown settings page '" + page + "'. Use one of "
                                    + SETTINGS.keySet() + "."));
                    openUri("ms-settings:" + SETTINGS.get(key), "Settings");
                    return ToolResult.ok("Opened Windows Settings: " + key + ".");
                }
                default -> throw new ToolException("Unknown action " + action);
            }
        }

        @Override
        public String describeCall(ToolArgs args) {
            String a = args.string("action");
            return switch (a) {
                case "compose_email" -> "Prepare an email" + (args.has("to") ? " to " + args.string("to") : "")
                        + (args.has("subject") ? " — \"" + args.string("subject") + "\"" : "") + " (you press Send)";
                case "whatsapp_message" -> "Open WhatsApp with \"" + args.string("text", "") + "\" ready"
                        + (args.has("recipient") ? " for " + args.string("recipient") : "") + " (you press Send)";
                case "windows_settings" -> "Open Windows Settings: " + args.string("page", "");
                case "maps_directions" -> "Open Maps directions to " + args.string("destination", "");
                default -> a.replace('_', ' ').replaceFirst("^(\\w+) search", "Search $1") + " for \""
                        + args.string("query", "") + "\"";
            };
        }
    }
}
