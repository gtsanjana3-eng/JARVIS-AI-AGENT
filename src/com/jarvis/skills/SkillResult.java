package com.jarvis.skills;

/**
 * What a skill returns: the spoken reply plus an optional action for the
 * frontend to perform (e.g. OPEN_URL, REFRESH_NOTES, CLEAR_CHAT).
 */
public record SkillResult(String reply, String action, String payload) {

    public static SkillResult say(String reply) {
        return new SkillResult(reply, null, null);
    }

    public static SkillResult withAction(String reply, String action, String payload) {
        return new SkillResult(reply, action, payload);
    }
}
