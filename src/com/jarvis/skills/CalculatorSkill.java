package com.jarvis.skills;

import com.jarvis.util.MathEvaluator;

/** Evaluates arithmetic such as "calculate (12 + 8) * 3" or "what is 15 percent of 200". */
public class CalculatorSkill extends Skill {

    public CalculatorSkill() {
        super("Calculator", "Utility", "Solves arithmetic with + - * / ^ and brackets.", "Calculate (25 + 15) * 3");
    }

    @Override
    public boolean canHandle(String c) {
        String expr = extract(c);
        return expr.matches("[0-9+\\-*/^%.() ]+") && expr.matches(".*\\d.*[-+*/^%].*\\d.*");
    }

    @Override
    public SkillResult execute(String c, String original, SkillContext ctx) {
        String expr = extract(c);
        try {
            return SkillResult.say(expr + " equals " + MathEvaluator.format(MathEvaluator.evaluate(expr)) + ".");
        } catch (RuntimeException e) {
            return SkillResult.say("I couldn't compute that: " + e.getMessage() + ".");
        }
    }

    private static String extract(String c) {
        return c.replaceAll("^(calculate|compute|what is|what's|whats|solve|evaluate)\\s+", "")
                .replaceAll("(\\d)\\s*(%|percent) of\\s*", "$1 / 100 * ")
                .replace(" x ", " * ").replace("multiplied by", "*").replace("times", "*")
                .replace("plus", "+").replace("minus", "-").replace("divided by", "/")
                .replace("?", "").replace("=", "").trim();
    }
}
