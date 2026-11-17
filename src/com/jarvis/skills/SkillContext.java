package com.jarvis.skills;

import com.jarvis.db.MessageRepository;
import com.jarvis.db.NoteRepository;
import com.jarvis.system.SystemMonitor;

import java.util.List;

/** Everything a skill may need access to, passed in instead of using globals. */
public record SkillContext(MessageRepository messages,
                           NoteRepository notes,
                           SystemMonitor monitor,
                           List<Skill> skills) {
}
