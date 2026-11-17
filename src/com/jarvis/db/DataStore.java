package com.jarvis.db;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

/**
 * Opens the SQLite database and owns one repository per table.
 *
 * OOP: COMPOSITION. The rest of the program asks the DataStore for the
 * repository it needs instead of creating database connections itself.
 */
public class DataStore implements AutoCloseable {

    private final Database db;
    public final ConversationRepository conversations;
    public final MessageRepository messages;
    public final NoteRepository notes;
    public final MemoryRepository memories;
    public final ReminderRepository reminders;
    public final TaskRepository tasks;
    public final AgentRunRepository runs;
    public final AgentStepRepository steps;
    public final ApprovalRepository approvals;

    public DataStore(Path dataDir) throws SQLException {
        this.db = new Database(dataDir.resolve("jarvis.sqlite"));
        this.conversations = new ConversationRepository(db);
        this.messages = new MessageRepository(db);
        this.notes = new NoteRepository(db);
        this.memories = new MemoryRepository(db);
        this.reminders = new ReminderRepository(db);
        this.tasks = new TaskRepository(db);
        this.runs = new AgentRunRepository(db);
        this.steps = new AgentStepRepository(db);
        this.approvals = new ApprovalRepository(db);

        new LegacyImporter(db, this).importIfNeeded(dataDir);
        // Anything that was mid-flight when JARVIS last stopped cannot resume.
        approvals.expireAllPending();
        runs.failInterrupted();
    }

    public Database database() {
        return db;
    }

    /** Every repository, for the database overview screen. */
    public List<Repository<?>> all() {
        return List.of(conversations, messages, notes, memories, reminders, tasks, runs, steps, approvals);
    }

    @Override
    public void close() {
        db.close();
    }
}
