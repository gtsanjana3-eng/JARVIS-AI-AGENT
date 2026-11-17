package com.jarvis.util;

/**
 * Safe arithmetic evaluator (recursive-descent parser) — the AI never "guesses"
 * a calculation, it calls this code.
 *
 * <pre>
 * expression := term (('+' | '-') term)*
 * term       := factor (('*' | '/' | '%') factor)*
 * factor     := ('+' | '-') factor | primary ('^' factor)?
 * primary    := number | constant | function '(' expression ')' | '(' expression ')'
 * </pre>
 * Functions: sqrt, abs, sin, cos, tan (degrees), log (base 10), ln, round, floor, ceil.
 * Constants: pi, e.
 */
public final class MathEvaluator {

    private final String s;
    private int pos = -1;
    private int ch;

    private MathEvaluator(String s) {
        this.s = s.toLowerCase().replace('×', '*').replace('÷', '/').replace(",", "");
    }

    public static double evaluate(String expression) {
        MathEvaluator p = new MathEvaluator(expression);
        p.next();
        double x = p.expression();
        p.skipSpaces();
        if (p.pos < p.s.length()) {
            throw new IllegalArgumentException("unexpected symbol '" + (char) p.ch + "'");
        }
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            throw new ArithmeticException("result is not a finite number");
        }
        return x;
    }

    /** Formats a result without needless decimals: 120.0 → "120". */
    public static String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        return String.valueOf(Math.round(v * 1e8) / 1e8);
    }

    private void next() {
        ch = (++pos < s.length()) ? s.charAt(pos) : -1;
    }

    private void skipSpaces() {
        while (ch == ' ') {
            next();
        }
    }

    private boolean eat(int c) {
        skipSpaces();
        if (ch == c) {
            next();
            return true;
        }
        return false;
    }

    private double expression() {
        double x = term();
        while (true) {
            if (eat('+')) {
                x += term();
            } else if (eat('-')) {
                x -= term();
            } else {
                return x;
            }
        }
    }

    private double term() {
        double x = factor();
        while (true) {
            if (eat('*')) {
                x *= factor();
            } else if (eat('/')) {
                double d = factor();
                if (d == 0) {
                    throw new ArithmeticException("division by zero");
                }
                x /= d;
            } else if (eat('%')) {
                x %= factor();
            } else {
                return x;
            }
        }
    }

    private double factor() {
        if (eat('+')) {
            return factor();
        }
        if (eat('-')) {
            return -factor();
        }
        double x = primary();
        if (eat('^')) {
            x = Math.pow(x, factor());
        }
        return x;
    }

    private double primary() {
        skipSpaces();
        int start = pos;
        if (eat('(')) {
            double x = expression();
            if (!eat(')')) {
                throw new IllegalArgumentException("missing closing bracket");
            }
            return x;
        }
        if ((ch >= '0' && ch <= '9') || ch == '.') {
            while ((ch >= '0' && ch <= '9') || ch == '.') {
                next();
            }
            return Double.parseDouble(s.substring(start, pos));
        }
        if (ch >= 'a' && ch <= 'z') {
            while (ch >= 'a' && ch <= 'z') {
                next();
            }
            String name = s.substring(start, pos);
            if (name.equals("pi")) {
                return Math.PI;
            }
            if (name.equals("e")) {
                return Math.E;
            }
            if (!eat('(')) {
                throw new IllegalArgumentException("unknown name '" + name + "'");
            }
            double a = expression();
            if (!eat(')')) {
                throw new IllegalArgumentException("missing closing bracket after " + name);
            }
            return switch (name) {
                case "sqrt" -> {
                    if (a < 0) {
                        throw new ArithmeticException("square root of a negative number");
                    }
                    yield Math.sqrt(a);
                }
                case "abs" -> Math.abs(a);
                case "sin" -> Math.sin(Math.toRadians(a));
                case "cos" -> Math.cos(Math.toRadians(a));
                case "tan" -> Math.tan(Math.toRadians(a));
                case "log" -> Math.log10(a);
                case "ln" -> Math.log(a);
                case "round" -> (double) Math.round(a);
                case "floor" -> Math.floor(a);
                case "ceil" -> Math.ceil(a);
                default -> throw new IllegalArgumentException("unknown function '" + name + "'");
            };
        }
        throw new IllegalArgumentException("expected a number");
    }
}
