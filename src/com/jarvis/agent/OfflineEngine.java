package com.jarvis.agent;

import com.jarvis.db.DataStore;
import com.jarvis.skills.CalculatorSkill;
import com.jarvis.skills.DateSkill;
import com.jarvis.skills.GreetingSkill;
import com.jarvis.skills.HelpSkill;
import com.jarvis.skills.IdentitySkill;
import com.jarvis.skills.JokeSkill;
import com.jarvis.skills.ListNotesSkill;
import com.jarvis.skills.NoteSkill;
import com.jarvis.skills.Skill;
import com.jarvis.skills.SkillContext;
import com.jarvis.skills.SystemStatusSkill;
import com.jarvis.skills.TimeSkill;
import com.jarvis.system.SystemMonitor;

import java.util.List;

/**
 * Limited fallback used only when the AI engine is unavailable. It reuses the
 * original rule-based {@link Skill} classes (polymorphic dispatch), and it is
 * always labelled as offline mode — it never pretends to be the AI.
 */
public class OfflineEngine {

    /** Result of handling a command without the AI. */
    public record Result(String reply, String skillName) {
    }

    private final List<Skill> skills;
    private final SkillContext context;

    public OfflineEngine(DataStore store, SystemMonitor monitor) {
        this.skills = List.of(
                new NoteSkill(),
                new ListNotesSkill(),
                new CalculatorSkill(),
                new TimeSkill(),
                new DateSkill(),
                new SystemStatusSkill(),
                new GreetingSkill(),
                new IdentitySkill(),
                new JokeSkill(),
                new HelpSkill());
        this.context = new SkillContext(store.messages, store.notes, monitor, skills);
    }

    /** Returns a reply from a matching skill, or null when no skill understands the text. */
    public Result handle(String input) {
        String original = input.trim().replaceFirst("(?i)^(hey\\s+|ok\\s+|okay\\s+)?jarvis[,:]?\\s+", "").trim();
        String command = original.toLowerCase().replaceAll("[.!?]+$", "").trim();
        if (command.isEmpty()) {
            command = "jarvis";
        }
        for (Skill s : skills) {
            if (s.canHandle(command)) {
                return new Result(s.execute(command, original, context).reply(), s.getName());
            }
        }
        return null;
    }
}
