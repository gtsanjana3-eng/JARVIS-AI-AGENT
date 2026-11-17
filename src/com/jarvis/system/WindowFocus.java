package com.jarvis.system;

import java.util.concurrent.TimeUnit;

/**
 * Brings a newly opened app window in front of the browser, so apps open
 * "on top of JARVIS" instead of behind it.
 *
 * Windows only lets the active program change the foreground window, and
 * JARVIS's Java server runs in the background. This uses Windows' built-in
 * WScript.Shell automation (through PowerShell): a harmless Alt key-press
 * unlocks the foreground, then AppActivate brings the window forward. It
 * retries for a few seconds while the app is starting. Best effort: if
 * Windows refuses, the app is still open on the taskbar.
 */
public final class WindowFocus {

    private WindowFocus() {
    }

    /**
     * @param titleHint text the window title starts or ends with (e.g. "Notepad", "WhatsApp")
     * @param pid       the process id if known, else 0
     */
    public static void bringToFrontAsync(String titleHint, long pid) {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            return;
        }
        String hint = titleHint == null ? "" : titleHint.replace("'", "''");
        String script = "$w = New-Object -ComObject WScript.Shell; "
                + "for ($i = 0; $i -lt 16; $i++) { "
                + "  Start-Sleep -Milliseconds 500; $w.SendKeys('%'); "
                + (pid > 0 ? "  if ($w.AppActivate(" + pid + ")) { break }; " : "")
                + (hint.isBlank() ? "" : "  if ($w.AppActivate('" + hint + "')) { break }; ")
                + "}";
        Thread t = new Thread(() -> {
            try {
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                        "-Command", script).redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (!p.waitFor(15, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (Exception ignored) {
                // focusing is a convenience; the app itself is already open
            }
        }, "jarvis-focus");
        t.setDaemon(true);
        t.start();
    }
}
