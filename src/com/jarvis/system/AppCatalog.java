package com.jarvis.system;

import com.jarvis.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Every application installed on this Windows PC that the user could start
 * from the Start menu: classic programs (Start-menu shortcuts) and Microsoft
 * Store apps such as WhatsApp (via the built-in Get-StartApps command).
 * JARVIS uses it to open any app by name instead of a fixed list.
 */
public class AppCatalog {

    /** One launchable application. */
    public record App(String name, Kind kind, String target) {
        public enum Kind { SHORTCUT, STORE_APP, EXECUTABLE }
    }

    private static final List<String> NOISE = List.of("uninstall", "readme", "read me", "help", "documentation",
            "release notes", "license", "website", "manual", "changelog", "support", "debug", "safe mode");

    private volatile Map<String, App> apps;
    private long loadedAt;

    /** All apps, loaded on first use and refreshed every 10 minutes (new installs appear automatically). */
    public synchronized Map<String, App> apps() {
        if (apps == null || System.currentTimeMillis() - loadedAt > 600_000) {
            apps = load();
            loadedAt = System.currentTimeMillis();
        }
        return apps;
    }

    /** Everyday nicknames → the name used in the Start menu. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("vs code", "visual studio code"), Map.entry("vscode", "visual studio code"),
            Map.entry("code", "visual studio code"), Map.entry("ppt", "powerpoint"),
            Map.entry("explorer", "file explorer"), Map.entry("files", "file explorer"),
            Map.entry("calc", "calculator"), Map.entry("cmd", "command prompt"),
            Map.entry("ms word", "word"), Map.entry("ms excel", "excel"), Map.entry("browser", "google chrome"),
            Map.entry("google", "google chrome"));

    /** Best match for what the user called the app, or null. */
    public App find(String spoken) {
        String q = norm(spoken);
        q = ALIASES.getOrDefault(q, q);
        if (q.isEmpty()) {
            return null;
        }
        Map<String, App> all = apps();
        App best = null;
        int bestScore = 0;
        for (App a : all.values()) {
            int s = score(q, norm(a.name()));
            if (s > bestScore || (s == bestScore && best != null && a.name().length() < best.name().length())) {
                best = a;
                bestScore = s;
            }
        }
        return bestScore >= 50 ? best : null;
    }

    /** Apps whose name contains any word of the query — used to suggest alternatives. */
    public List<String> suggestions(String spoken, int limit) {
        String q = norm(spoken);
        return apps().values().stream()
                .map(App::name)
                .filter(n -> q.isEmpty() || List.of(q.split(" ")).stream().anyMatch(w -> w.length() > 2 && norm(n).contains(w)))
                .sorted()
                .limit(limit)
                .toList();
    }

    /**
     * How well an app name matches what the user said. Strict on purpose: every word the user said must be a
     * whole word of the app's name (so "stored information" can never match "Microsoft Store").
     */
    private static int score(String q, String name) {
        if (name.equals(q)) {
            return 100;
        }
        List<String> nameWords = List.of(name.split(" "));
        String[] words = q.split(" ");
        for (String w : words) {
            if (!nameWords.contains(w)) {
                return 0;
            }
        }
        return 60 + Math.min(30, 30 * words.length / Math.max(1, nameWords.size()));
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(the|app|application|program|microsoft)\\b", " ")
                .replaceAll("[^a-z0-9+ ]", " ").replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------------ loading

    private Map<String, App> load() {
        Map<String, App> out = new LinkedHashMap<>();
        // Built-in Windows tools that have no Start-menu shortcut on every PC.
        put(out, new App("Notepad", App.Kind.EXECUTABLE, "notepad.exe"));
        put(out, new App("Calculator", App.Kind.EXECUTABLE, "calc.exe"));
        put(out, new App("Paint", App.Kind.EXECUTABLE, "mspaint.exe"));
        put(out, new App("File Explorer", App.Kind.EXECUTABLE, "explorer.exe"));
        put(out, new App("Task Manager", App.Kind.EXECUTABLE, "taskmgr.exe"));
        put(out, new App("Control Panel", App.Kind.EXECUTABLE, "control.exe"));
        put(out, new App("Snipping Tool", App.Kind.EXECUTABLE, "snippingtool.exe"));

        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            return out;
        }
        for (String env : new String[] { "ProgramData", "APPDATA" }) {
            String base = System.getenv(env);
            if (base == null) {
                continue;
            }
            Path dir = Path.of(base, "Microsoft", "Windows", "Start Menu", "Programs");
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(dir, 4)) {
                s.filter(p -> p.toString().toLowerCase().endsWith(".lnk")).forEach(p -> {
                    String name = p.getFileName().toString().replaceAll("(?i)\\.lnk$", "");
                    if (NOISE.stream().noneMatch(n -> name.toLowerCase().contains(n))) {
                        put(out, new App(name, App.Kind.SHORTCUT, p.toString()));
                    }
                });
            } catch (IOException ignored) {
                // unreadable folder: skip
            }
        }
        loadStoreApps(out);
        return out;
    }

    /** Microsoft Store apps (WhatsApp, Spotify from the Store, Settings...) via Windows' Get-StartApps. */
    private static void loadStoreApps(Map<String, App> out) {
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "[Console]::OutputEncoding=[Text.Encoding]::UTF8; Get-StartApps | Select-Object Name,AppID | ConvertTo-Json -Compress")
                    .redirectErrorStream(true).start();
            String json = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!p.waitFor(20, TimeUnit.SECONDS) || json.isEmpty()) {
                p.destroyForcibly();
                return;
            }
            Object parsed = Json.parseAny(json);
            List<Object> list = parsed instanceof List<?> ? Json.asList(parsed) : List.of(parsed);
            for (Object o : list) {
                Map<String, Object> m = Json.asMap(o);
                String name = Json.asString(m.get("Name"), "");
                String id = Json.asString(m.get("AppID"), "");
                // Only Store-style IDs ("Package!App"); classic programs are already covered by shortcuts.
                if (!name.isBlank() && id.contains("!") && NOISE.stream().noneMatch(n -> name.toLowerCase().contains(n))) {
                    out.putIfAbsent(name.toLowerCase(Locale.ROOT), new App(name, App.Kind.STORE_APP, id));
                }
            }
        } catch (IOException | RuntimeException e) {
            // PowerShell unavailable or output unreadable: shortcuts still work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void put(Map<String, App> out, App app) {
        out.putIfAbsent(app.name().toLowerCase(Locale.ROOT), app);
    }

    /** Names sorted alphabetically (for listing). */
    public List<String> names() {
        List<String> n = new ArrayList<>(apps().values().stream().map(App::name).toList());
        n.sort(Comparator.comparing(String::toLowerCase));
        return n;
    }
}
