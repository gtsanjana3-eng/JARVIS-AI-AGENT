package com.jarvis.skills;

public class IdentitySkill extends Skill {

    public IdentitySkill() {
        super("Identity", "Core", "Explains who JARVIS is and how it was built.", "Who are you?");
    }

    @Override
    public boolean canHandle(String c) {
        return containsAny(c, "who are you", "your name", "what are you", "introduce yourself");
    }

    @Override
    public SkillResult execute(String c, String original, SkillContext ctx) {
        return SkillResult.say("I am JARVIS, Just A Rather Very Intelligent System. My core runs entirely on Java, "
                + "and I currently command " + ctx.skills().size() + " protocols.");
    }
}
