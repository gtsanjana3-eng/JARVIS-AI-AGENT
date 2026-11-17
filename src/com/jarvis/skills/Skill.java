package com.jarvis.skills;

/**
 * A single ability ("protocol") of JARVIS — telling the time, saving a note, etc.
 *
 * OOP: ABSTRACTION + POLYMORPHISM. The assistant keeps a List&lt;Skill&gt; and
 * asks each one {@link #canHandle}. It never needs to know which concrete
 * class it is talking to — each subclass overrides {@link #execute} with its
 * own behaviour. Adding a new ability = adding one new subclass.
 */
public abstract class Skill {

    private final String name;
    private final String description;
    private final String example;
    private final String category;

    protected Skill(String name, String category, String description, String example) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.example = example;
    }

    /** Does this skill understand the (lower-cased, trimmed) command? */
    public abstract boolean canHandle(String command);

    /** Carry out the command. {@code original} keeps the user's original casing. */
    public abstract SkillResult execute(String command, String original, SkillContext context);

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getExample() {
        return example;
    }

    /** Helper for subclasses: true if the command contains any of the given phrases. */
    protected static boolean containsAny(String command, String... phrases) {
        for (String p : phrases) {
            if (command.contains(p)) {
                return true;
            }
        }
        return false;
    }
}
