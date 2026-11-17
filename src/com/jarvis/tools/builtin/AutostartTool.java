package com.jarvis.tools.builtin;

import com.jarvis.tools.RiskLevel;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolArgs;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolParam;
import com.jarvis.tools.ToolResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Makes JARVIS start silently in the background whenever Windows starts, so reminders reach the user
 * even when they never open the JARVIS page. Adds (or removes) a small launcher in the user's Windows
 * Startup folder. Changing startup behaviour is EXTERNAL, so the user approves it.
 */
public class AutostartTool extends Tool {

    public AutostartTool() {
        super("autostart", "system",
                "Turn on or off starting JARVIS automatically (in the background) when Windows starts.",
                RiskLevel.EXTERNAL, ToolParam.choice("action", true, "on or off", "on", "off"));
    }

    static Path launcher() throws ToolException {
        String appData = System.getenv("APPDATA");
        if (appData == null) {
            throw new ToolException("Starting with Windows is only supported on Windows.");
        }
        return Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup", "JARVIS.bat");
    }

    @Override
    protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
        Path file = launcher();
        if (args.string("action").equals("off")) {
            if (!Files.deleteIfExists(file)) {
                return ToolResult.ok("JARVIS was not set to start with Windows, so nothing changed.");
            }
            return ToolResult.ok("Done. JARVIS will no longer start automatically with Windows.");
        }
        Path project = Path.of("").toAbsolutePath();
        String port = System.getProperty("jarvis.port", "8080");
        String bat = "@echo off\r\n"
                + "rem Starts J.A.R.V.I.S. in the background (no window) so reminders work after login.\r\n"
                + "cd /d \"" + project + "\"\r\n"
                + "start \"\" javaw --enable-native-access=ALL-UNNAMED -cp \"" + project.resolve("out") + ";"
                + project.resolve("lib") + "\\*\" com.jarvis.Main " + port + " --no-browser\r\n";
        Files.createDirectories(file.getParent());
        Files.writeString(file, bat, StandardCharsets.UTF_8);
        return ToolResult.ok("Done. JARVIS will now start silently whenever Windows starts, so reminders reach you "
                + "even if you never open the page. Look for the JARVIS icon next to the clock. Say \"stop starting "
                + "with Windows\" to undo.");
    }

    @Override
    public String describeCall(ToolArgs args) {
        return args.string("action").equals("on") ? "Start JARVIS automatically with Windows"
                : "Stop starting JARVIS with Windows";
    }
}
