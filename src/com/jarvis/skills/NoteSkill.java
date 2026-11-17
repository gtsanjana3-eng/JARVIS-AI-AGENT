package com.jarvis.skills;

import com.jarvis.model.Note;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Saves a note to the database: "remember ...", "take a note ...", "note ...". */
public class NoteSkill extends Skill {

    private static final Pattern PATTERN = Pattern.compile(
            "^(?:please\\s+)?(?:remember(?: that)?|take a note(?: that)?|make a note(?: that)?|note(?: that)?|save note)[:,]?\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public NoteSkill() {
        super("Memory Write", "Memory", "Stores a note permanently in the database.",
                "Remember to submit the Java project on Friday");
    }

    @Override
    public boolean canHandle(String c) {
        return PATTERN.matcher(c).matches();
    }

    @Override
    public SkillResult execute(String c, String original, SkillContext ctx) {
        Matcher m = PATTERN.matcher(original.trim());
        if (!m.matches()) {
            return SkillResult.say("I didn't catch what you'd like me to remember.");
        }
        String content = m.group(1).trim();
        // "Remember to buy milk" reads better stored whole than as "to buy milk".
        if (content.toLowerCase().startsWith("to ")) {
            content = original.trim();
        }
        Note note = ctx.notes().save(new Note(content));
        return SkillResult.withAction("Noted. I've stored that in memory as entry number " + note.getId() + ".",
                "REFRESH_NOTES", null);
    }
}
