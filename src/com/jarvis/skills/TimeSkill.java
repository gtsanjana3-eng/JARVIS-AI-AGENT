package com.jarvis.skills;

import com.jarvis.util.SpokenTime;

import java.time.LocalTime;

public class TimeSkill extends Skill {

    public TimeSkill() {
        super("Clock", "Utility", "Reports the current local time from the server clock.", "What time is it?");
    }

    @Override
    public boolean canHandle(String c) {
        return c.matches(".*\\btime\\b.*");
    }

    @Override
    public SkillResult execute(String c, String original, SkillContext ctx) {
        return SkillResult.say("It's " + SpokenTime.time(LocalTime.now()) + ".");
    }
}
