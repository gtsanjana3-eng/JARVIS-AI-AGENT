package com.jarvis.agent.workflow;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the user wants a document to end up: which file format to create and
 * which app to open it in ("in Word" → .docx in Word, "in Notepad" → .txt).
 */
public record AppTarget(String format, String app, String label) {

    public static final AppTarget WORD = new AppTarget("docx", "word", "Word");
    public static final AppTarget NOTEPAD = new AppTarget("txt", "notepad", "Notepad");
    public static final AppTarget BROWSER = new AppTarget("html", "browser", "your browser");
    public static final AppTarget VSCODE = new AppTarget("md", "vscode", "VS Code");

    private static final Pattern ANY = Pattern.compile(
            "\\b(ms word|microsoft word|word|docx|notepad|note pad|text file|txt|chrome|edge|browser|web ?page|html|"
                    + "vs ?code|visual studio code|markdown|document)\\b", Pattern.CASE_INSENSITIVE);

    /** Parses a phrase that names only the target ("Word", "a text file", "the browser"). */
    public static Optional<AppTarget> parse(String spoken) {
        if (spoken == null) {
            return Optional.empty();
        }
        String s = spoken.toLowerCase().replaceAll("^(a |an |the |my )", "").trim();
        return s.isEmpty() ? Optional.empty() : find(s);
    }

    /** Finds a target mentioned anywhere in a sentence ("…and store it in the word" → Word). */
    public static Optional<AppTarget> find(String sentence) {
        if (sentence == null) {
            return Optional.empty();
        }
        // Speech recognition often hears "word" as "world": "put it in the world" means Word.
        if (sentence.matches("(?is).*\\b(?:put|save|store|write|keep|add)\\b.*\\b(?:in|into|to|on) (?:the |a )?world\\b.*")) {
            return Optional.of(WORD);
        }
        Matcher m = ANY.matcher(sentence);
        if (!m.find()) {
            return Optional.empty();
        }
        String k = m.group(1).toLowerCase();
        if (k.contains("word") || k.equals("docx") || k.equals("document")) {
            return Optional.of(WORD);
        }
        if (k.contains("note") || k.contains("text") || k.equals("txt")) {
            return Optional.of(NOTEPAD);
        }
        if (k.contains("code") || k.equals("markdown")) {
            return Optional.of(VSCODE);
        }
        return Optional.of(BROWSER);
    }
}
