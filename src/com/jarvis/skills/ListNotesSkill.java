package com.jarvis.skills;

import com.jarvis.model.Note;

import java.util.List;

public class ListNotesSkill extends Skill {

    public ListNotesSkill() {
        super("Memory Recall", "Memory", "Reads back the notes stored in the database.", "Show my notes");
    }

    @Override
    public boolean canHandle(String c) {
        return containsAny(c, "my notes", "show notes", "list notes", "read notes", "what do you remember",
                "what did i ask you to remember");
    }

    @Override
    public SkillResult execute(String c, String original, SkillContext ctx) {
        List<Note> notes = ctx.notes().findAll();
        if (notes.isEmpty()) {
            return SkillResult.say("Your memory bank is empty. Say remember, followed by anything, to store a note.");
        }
        StringBuilder sb = new StringBuilder("You have " + notes.size() + (notes.size() == 1 ? " note" : " notes") + ". ");
        int from = Math.max(0, notes.size() - 5);
        for (int i = from; i < notes.size(); i++) {
            sb.append(i - from + 1).append(": ").append(notes.get(i).getContent()).append(". ");
        }
        return SkillResult.withAction(sb.toString().trim(), "SHOW_VIEW", "memory");
    }
}
