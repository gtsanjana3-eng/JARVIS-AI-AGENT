package com.jarvis.skills;

import java.util.stream.Collectors;

public class HelpSkill extends Skill {

    public HelpSkill() {
        super("Protocol List", "Core", "Lists everything JARVIS can do.", "What can you do?");
    }

    @Override
    public boolean canHandle(String c) {
        return containsAny(c, "help", "what can you do", "commands", "protocols", "abilities");
    }

    @Override
    public SkillResult execute(String c, String original, SkillContext ctx) {
        String names = ctx.skills().stream().map(Skill::getName).collect(Collectors.joining(", "));
        return SkillResult.withAction("I currently have " + ctx.skills().size() + " protocols online: " + names + ".",
                "SHOW_VIEW", "protocols");
    }
}
