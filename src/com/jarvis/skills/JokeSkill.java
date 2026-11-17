package com.jarvis.skills;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class JokeSkill extends Skill {

    private static final List<String> JOKES = List.of(
            "Why do Java developers wear glasses? Because they don't C sharp.",
            "I would tell you a UDP joke, but you might not get it.",
            "There are 10 kinds of people in the world: those who understand binary, and those who don't.",
            "A SQL query walks into a bar, approaches two tables and asks: may I join you?",
            "Why was the Java class so calm? It had excellent exception handling.",
            "My favourite design pattern is the Singleton. There is only one of it, much like me.",
            "Debugging is like being the detective in a crime movie where you are also the culprit.");

    private int last = -1;

    public JokeSkill() {
        super("Humour", "Core", "Tells a programming joke. Results may vary.", "Tell me a joke");
    }

    @Override
    public boolean canHandle(String c) {
        return containsAny(c, "joke", "make me laugh", "something funny");
    }

    @Override
    public synchronized SkillResult execute(String c, String original, SkillContext ctx) {
        int i;
        do {
            i = ThreadLocalRandom.current().nextInt(JOKES.size());
        } while (i == last);
        last = i;
        return SkillResult.say(JOKES.get(i));
    }
}
