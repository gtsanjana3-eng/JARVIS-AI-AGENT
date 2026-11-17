package com.jarvis.skills;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateSkill extends Skill {

    public DateSkill() {
        super("Calendar", "Utility", "Tells today's date and day of the week.", "What is today's date?");
    }

    @Override
    public boolean canHandle(String c) {
        return containsAny(c, "date", "what day", "which day", "today");
    }

    @Override
    public SkillResult execute(String c, String original, SkillContext ctx) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"));
        return SkillResult.say("Today is " + today + ".");
    }
}
