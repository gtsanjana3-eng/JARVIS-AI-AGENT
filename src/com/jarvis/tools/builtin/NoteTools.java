package com.jarvis.tools.builtin;

import com.jarvis.model.Note;
import com.jarvis.tools.RiskLevel;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolArgs;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolParam;
import com.jarvis.tools.ToolResult;

import java.util.List;

import static com.jarvis.tools.ToolParam.Type.INTEGER;
import static com.jarvis.tools.ToolParam.Type.STRING;

/** Tools that create, find and delete notes in the database. */
public final class NoteTools {

    private NoteTools() {
    }

    public static class SaveNote extends Tool {
        public SaveNote() {
            super("save_note", "notes", "Save a note to the database (lecture notes, ideas, summaries, lists).",
                    RiskLevel.SAFE,
                    ToolParam.required("content", STRING, "Full text of the note"),
                    ToolParam.optional("title", STRING, "Short title"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) {
            Note n = ctx.store().notes.save(new Note(args.string("title", null), args.string("content")));
            return ToolResult.ok("Note #" + n.getId() + " saved" + (n.getTitle() != null ? " ('" + n.getTitle() + "')" : "") + ".");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Save note: " + args.string("title", args.string("content"));
        }
    }

    public static class SearchNotes extends Tool {
        public SearchNotes() {
            super("search_notes", "notes", "List saved notes, optionally filtered by a search word.", RiskLevel.SAFE,
                    ToolParam.optional("query", STRING, "Word or phrase to search for; omit to list all notes"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) {
            List<Note> notes = args.has("query") ? ctx.store().notes.search(args.string("query"))
                    : ctx.store().notes.findAll();
            if (notes.isEmpty()) {
                return ToolResult.ok(args.has("query") ? "No notes match '" + args.string("query") + "'."
                        : "There are no saved notes.");
            }
            StringBuilder sb = new StringBuilder(notes.size() + " note(s):\n");
            for (Note n : notes.subList(Math.max(0, notes.size() - 25), notes.size())) {
                sb.append("#").append(n.getId()).append(' ');
                if (n.getTitle() != null) {
                    sb.append('[').append(n.getTitle()).append("] ");
                }
                sb.append(n.getContent()).append('\n');
            }
            return ToolResult.ok(sb.toString().trim());
        }

        @Override
        public String describeCall(ToolArgs args) {
            return args.has("query") ? "Search notes for '" + args.string("query") + "'" : "List notes";
        }
    }

    public static class DeleteNote extends Tool {
        public DeleteNote() {
            super("delete_note", "notes", "Permanently delete a note by its id number.", RiskLevel.DESTRUCTIVE,
                    ToolParam.required("id", INTEGER, "Note id, e.g. 3"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            long id = args.integer("id");
            if (!ctx.store().notes.delete(id)) {
                throw new ToolException("There is no note #" + id + ".");
            }
            return ToolResult.ok("Note #" + id + " deleted.");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Delete note #" + args.integer("id");
        }
    }
}
