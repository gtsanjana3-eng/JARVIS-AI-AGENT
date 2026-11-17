package com.jarvis.db;

import com.jarvis.model.Conversation;
import com.jarvis.model.Message;
import com.jarvis.model.Note;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time import of the data saved by the first version of JARVIS
 * (data/messages.db and data/notes.db, tab-separated text files) into SQLite.
 * The old files are left untouched as a backup.
 */
class LegacyImporter {

    private static final String DONE_KEY = "legacy_import_done";

    private final Database db;
    private final DataStore store;

    LegacyImporter(Database db, DataStore store) {
        this.db = db;
        this.store = store;
    }

    void importIfNeeded(Path dataDir) {
        if (db.getMeta(DONE_KEY) != null) {
            return;
        }
        int messages = 0;
        int notes = 0;
        try {
            List<String[]> msgRows = readRows(dataDir.resolve("messages.db"));
            if (!msgRows.isEmpty()) {
                long first = Long.parseLong(msgRows.get(0)[3]);
                long last = Long.parseLong(msgRows.get(msgRows.size() - 1)[3]);
                Conversation c = new Conversation(0, "Earlier conversation (imported)", last, first);
                store.conversations.save(c);
                for (String[] r : msgRows) {
                    store.messages.save(new Message(0, c.getId(), Message.Role.valueOf(r[1]), r[2], null,
                            Long.parseLong(r[3])));
                    messages++;
                }
            }
            for (String[] r : readRows(dataDir.resolve("notes.db"))) {
                store.notes.save(new Note(0, null, r[1], Long.parseLong(r[2])));
                notes++;
            }
            db.setMeta(DONE_KEY, String.valueOf(System.currentTimeMillis()));
            if (messages + notes > 0) {
                System.out.println("  Imported " + messages + " messages and " + notes
                        + " notes from the previous version (old files kept as backup).");
            }
        } catch (IOException | RuntimeException e) {
            // Leave the flag unset so the import is retried next start; the old files stay intact.
            System.err.println("  Could not import old data files: " + e.getMessage());
        }
    }

    private static List<String[]> readRows(Path file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        if (!Files.exists(file)) {
            return rows;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            for (int i = 0; i < parts.length; i++) {
                parts[i] = unescape(parts[i]);
            }
            rows.add(parts);
        }
        return rows;
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 't' -> out.append('\t');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    default -> out.append(n);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
