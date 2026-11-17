package com.jarvis.agent.workflow;

import java.util.regex.Pattern;

/**
 * Splits a research request into its parts, however it is phrased:
 * "research about Taj Mahal and put it in the word" → topic "Taj Mahal", target Word;
 * "research dark web. I need deep information, five pages, store it in word" → topic "dark web", Word, detailed.
 */
public record ResearchRequest(String topic, AppTarget target, ResearchWorkflow.Depth depth, boolean targetGiven) {

    private static final Pattern APP_CLAUSE = Pattern.compile(
            "(?i)\\s*,?\\s*(?:and|then|&)?\\s*(?:please\\s+)?(?:save|put|write|type|open|show|create|make|add|paste|keep|"
                    + "store|display|give)?\\s*(?:it|this|that|them|the (?:results?|info(?:rmation)?|report))?\\s*"
                    + "(?:in|into|on|to|as|using|with)\\s+(?:a |an |the |my )?(?:ms |microsoft )?(?:word|world|notepad|note pad|"
                    + "text file|txt|docx|chrome|edge|browser|web ?page|html|vs ?code|visual studio code|document)\\b.*$");
    private static final Pattern DEEP = Pattern.compile(
            "(?i)\\b(deep(?:ly)?|detailed|in detail|in-depth|full|complete|entire|everything|long|\\d+ pages|"
                    + "(?:three|four|five|six|ten) pages|pages)\\b");
    private static final Pattern BRIEF = Pattern.compile("(?i)\\b(short|brief|quick|summary|summarise|summarize|small|"
            + "one page|1 page|few lines)\\b");
    private static final Pattern JUST_TELL = Pattern.compile("(?i)\\b(just tell me|tell me here|no document|"
            + "don'?t (?:save|write|create)|say it|read it out|here)\\b");

    public static ResearchRequest parse(String text) {
        return parse(text, text);
    }

    /**
     * @param text  the part after the command word ("Taj Mahal and put it in Word")
     * @param whole the full sentence, used to find the destination and depth ("make a detailed report on …")
     */
    public static ResearchRequest parse(String text, String whole) {
        String t = text.trim();
        AppTarget target = AppTarget.find(whole).orElse(null);
        ResearchWorkflow.Depth depth = depthOf(whole);
        // Topic = the words before the first sentence break or app clause.
        String topic = t.split("(?<=[a-zA-Z0-9)])[.;!?]\\s+|\\s*,\\s+(?:i |and |then |please |make |put |store |save )")[0];
        topic = APP_CLAUSE.matcher(topic).replaceFirst("");
        topic = topic.replaceAll("(?i)\\s*,?\\s*(?:and\\s+)?(?:just tell me|tell me here|say it|read it out)\\b.*$", "")
                .replaceAll("(?i)\\s+(?:and|then)\\s+(?:give|tell|show|write|make|create|prepare)\\b.*$", "")
                .replaceAll("(?i)\\b(?:in detail|in depth|deeply|in short|briefly|quickly)\\b", "")
                .replaceAll("(?i)^(?:about|on|regarding|the topic)\\s+", "")
                .replaceAll("\\s+", " ").replaceAll("[\\s,.]+$", "").trim();
        return new ResearchRequest(topic, target, depth, target != null || JUST_TELL.matcher(whole).find());
    }

    static ResearchWorkflow.Depth depthOf(String t) {
        if (DEEP.matcher(t).find()) {
            return ResearchWorkflow.Depth.DETAILED;
        }
        if (BRIEF.matcher(t).find()) {
            return ResearchWorkflow.Depth.BRIEF;
        }
        return null;
    }

    static boolean wantsJustAnswer(String t) {
        return JUST_TELL.matcher(t).find();
    }
}
