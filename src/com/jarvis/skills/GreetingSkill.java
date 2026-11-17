package com.jarvis.skills;

import java.time.LocalTime;

public class GreetingSkill extends Skill {

    public GreetingSkill() {
        super("Greeting", "Core", "Responds to greetings with a time-aware salutation.", "Hello Jarvis");
    }

    @Override
    public boolean canHandle(String c) {
        return c.matches("^(hi|hello|hey|good (morning|afternoon|evening)|wake up|yo)\\b.*")
                || c.equals("jarvis");
    }

    @Override
    public SkillResult execute(String c, String original, SkillContext ctx) {
        int hour = LocalTime.now().getHour();
        String part = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        return SkillResult.say(part + ". All systems are online and at your service. How may I assist you?");
    }
}
