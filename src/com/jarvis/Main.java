package com.jarvis;

import com.jarvis.agent.Agent;
import com.jarvis.agent.OfflineEngine;
import com.jarvis.agent.ReminderService;
import com.jarvis.ai.LlmClient;
import com.jarvis.ai.LlmStatus;
import com.jarvis.ai.OllamaClient;
import com.jarvis.config.Config;
import com.jarvis.core.EventBus;
import com.jarvis.core.StateManager;
import com.jarvis.db.DataStore;
import com.jarvis.search.SearchProvider;
import com.jarvis.search.WikipediaSearch;
import com.jarvis.server.ApiHandler;
import com.jarvis.server.EventStreamHandler;
import com.jarvis.server.StaticFileHandler;
import com.jarvis.system.AppCatalog;
import com.jarvis.system.DesktopNotifier;
import com.jarvis.system.SystemMonitor;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolRegistry;
import com.jarvis.tools.Workspace;
import com.jarvis.tools.builtin.BuiltinTools;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.concurrent.Executors;

/** Wires everything together: config, database, AI engine, tools, agent and web server. */
public class Main {

    public static void main(String[] args) throws Exception {
        useSafeSocketTempDir();

        Config config = new Config(Path.of("config", "jarvis.properties"));
        int port = args.length > 0 ? Integer.parseInt(args[0]) : config.port();

        DataStore store = new DataStore(Path.of("data"));
        SystemMonitor monitor = new SystemMonitor();
        EventBus bus = new EventBus();
        StateManager state = new StateManager(bus);
        Workspace workspace = new Workspace(config.workspaceDir());
        SearchProvider search = WikipediaSearch.fromConfig(config.searchProvider(), config.get("google.apiKey", ""),
                config.get("google.cx", ""), config.get("brave.apiKey", ""));
        ToolRegistry tools = BuiltinTools.createRegistry(search);
        LlmClient llm = new OllamaClient(config.ollamaUrl(), config.ollamaModel(), config.ollamaTimeoutSeconds(),
                config.ollamaContextTokens());
        AppCatalog apps = new AppCatalog();
        new Thread(apps::apps, "jarvis-app-scan").start(); // index installed apps in the background
        ToolContext toolContext = new ToolContext(store, monitor, workspace, ZoneId.systemDefault(), apps, llm);
        Agent agent = new Agent(store, llm, tools, toolContext, state, bus, config, new OfflineEngine(store, monitor));
        System.setProperty("jarvis.port", String.valueOf(port));
        DesktopNotifier notifier = new DesktopNotifier("http://localhost:" + port);
        new ReminderService(store, bus, notifier).start();
        agent.warmUpAsync();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/events", new EventStreamHandler(bus));
        server.createContext("/api/", new ApiHandler(agent, store, state, monitor, new com.jarvis.oop.OopInspector(tools, toolContext)));
        server.createContext("/", new StaticFileHandler(Path.of("web")));
        // Cached pool: each open browser tab holds one thread for its live event stream.
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(store::close));

        LlmStatus ai = llm.status();
        String url = "http://localhost:" + port;
        System.out.println("""
                ==============================================
                  J.A.R.V.I.S. agent online
                  Interface : %s
                  Database  : %s
                  Workspace : %s
                  AI engine : %s %s - %s
                  Search    : %s
                  Tools     : %d registered
                  Press Ctrl+C to shut down.
                ==============================================""".formatted(url, store.database().getFile(),
                workspace.root(), ai.provider(), ai.model(), ai.detail(), search.name(), tools.size()));
        if (!ai.ready()) {
            System.out.println("  NOTE: JARVIS will run in limited offline mode until the AI engine is ready.");
            System.out.println("        Setup steps are in README.md (section 'Set up the free AI engine').");
        }

        if (args.length < 2 || !args[1].equals("--no-browser")) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                }
            } catch (Exception ignored) {
                // Opening the browser is a convenience only.
            }
        }
    }

    /**
     * On Windows the JDK's network selector creates a Unix-domain socket in the
     * temp folder. If that path contains spaces or short "~1" names (e.g. a user
     * name like "SANJANA G T") the server fails with "Unable to establish
     * loopback connection". Point it at a simple folder instead.
     */
    private static void useSafeSocketTempDir() {
        if (System.getProperty("jdk.net.unixdomain.tmpdir") != null
                || !System.getProperty("os.name").toLowerCase().contains("win")) {
            return;
        }
        String base = System.getenv().getOrDefault("PUBLIC", "C:\\Users\\Public");
        Path dir = Path.of(base, "jarvis-tmp");
        try {
            java.nio.file.Files.createDirectories(dir);
            System.setProperty("jdk.net.unixdomain.tmpdir", dir.toString());
        } catch (java.io.IOException ignored) {
            // Fall back to the JDK default.
        }
    }
}
