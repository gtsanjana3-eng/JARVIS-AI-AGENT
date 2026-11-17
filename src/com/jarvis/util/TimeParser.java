package com.jarvis.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns everyday phrases into exact dates and times:
 * "in one second", "in half an hour", "tomorrow at 9am", "on Friday at 6 pm",
 * "next Monday", "14th November at 5:30 pm", "17:30", "2026-11-05 14:00".
 */
public final class TimeParser {

    private static final Pattern RELATIVE = Pattern.compile(
            "^in\\s+(\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?)$");
    private static final Pattern CLOCK = Pattern.compile("^(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)?$");
    private static final Pattern DAY_MONTH = Pattern.compile("^(\\d{1,2})(?:st|nd|rd|th)?(?: of)? ([a-z]+)\\b(.*)$");
    private static final Pattern MONTH_DAY = Pattern.compile("^([a-z]+) (\\d{1,2})(?:st|nd|rd|th)?\\b(.*)$");

    private static final Map<String, Integer> NUMBERS = Map.ofEntries(
            Map.entry("a", 1), Map.entry("an", 1), Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3),
            Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7),
            Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11),
            Map.entry("twelve", 12), Map.entry("fifteen", 15), Map.entry("twenty", 20), Map.entry("thirty", 30),
            Map.entry("forty", 40), Map.entry("forty-five", 45), Map.entry("fifty", 50), Map.entry("sixty", 60));

    private TimeParser() {
    }

    /** Converts spoken numbers ("in two minutes", "half an hour") into digits. */
    static String normalise(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ")
                .replace("a.m.", "am").replace("p.m.", "pm").replace("o'clock", "").replace("o clock", "")
                .replaceAll("\\bhalf an hour\\b", "30 minutes").replaceAll("\\ba quarter of an hour\\b", "15 minutes")
                .replaceAll("^after ", "in ").replaceAll("\\btonight\\b", "today 8pm").replaceAll("\\bnoon\\b", "12pm")
                .replaceAll("\\bmidnight\\b", "12am").replaceAll("\\bmorning\\b", "9am").replaceAll("\\bevening\\b", "6pm")
                .replaceAll("\\s+", " ").trim();
        Matcher m = Pattern.compile("\\b(a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|"
                + "twenty|thirty|forty-five|forty|fifty|sixty)\\b(?=\\s+(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?)\\b)")
                .matcher(t);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, String.valueOf(NUMBERS.get(m.group(1))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static ZonedDateTime parse(String text, ZonedDateTime now) {
        String t = normalise(text);

        Matcher rel = RELATIVE.matcher(t);
        if (rel.matches()) {
            double n = Double.parseDouble(rel.group(1));
            String unit = rel.group(2);
            long seconds = (long) (n * (unit.startsWith("s") ? 1 : unit.startsWith("m") ? 60
                    : unit.startsWith("h") ? 3600 : unit.startsWith("d") ? 86400 : 604800));
            return now.plusSeconds(seconds);
        }
        try {
            return LocalDateTime.parse(t.replace(' ', 'T')).atZone(now.getZone());
        } catch (DateTimeParseException ignored) {
            // try the next format
        }
        try {
            return ZonedDateTime.parse(text.trim()).withZoneSameInstant(now.getZone());
        } catch (DateTimeParseException ignored) {
            // try the next format
        }

        DayPart day = dayPart(t, now.toLocalDate());
        String rest = day.rest().replaceFirst("^(at|by|around)\\s+", "").trim();
        if (rest.isEmpty()) {
            rest = "9am";
        }
        Matcher clock = CLOCK.matcher(rest);
        if (!clock.matches()) {
            throw new IllegalArgumentException("I couldn't understand the time '" + text
                    + "'. Try 'in 30 minutes', 'tomorrow at 9am', 'on Friday at 6 pm' or '17:30'.");
        }
        int hour = Integer.parseInt(clock.group(1));
        int minute = clock.group(2) == null ? 0 : Integer.parseInt(clock.group(2));
        String ampm = clock.group(3);
        if (ampm != null) {
            if (hour < 1 || hour > 12) {
                throw new IllegalArgumentException("Invalid hour in '" + text + "'");
            }
            hour = hour % 12 + (ampm.equals("pm") ? 12 : 0);
        }
        if (hour > 23 || minute > 59) {
            throw new IllegalArgumentException("Invalid time in '" + text + "'");
        }
        ZonedDateTime result = day.date().atTime(LocalTime.of(hour, minute)).atZone(now.getZone());
        // "at 9am" said after 9am today (with no day given) means tomorrow.
        if (!day.explicit() && result.isBefore(now)) {
            result = result.plusDays(1);
        }
        return result;
    }

    /** A date such as "Friday", "next Monday", "tomorrow", "14 November" or "2026-11-20". */
    public static LocalDate parseDate(String text, LocalDate today) {
        String t = normalise(text).replaceFirst("^(by|on|before|due|for)\\s+", "");
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException ignored) {
            // spoken form
        }
        DayPart d = dayPart(t, today);
        if (!d.explicit()) {
            throw new IllegalArgumentException("I couldn't understand the date '" + text + "'.");
        }
        return d.date();
    }

    private record DayPart(LocalDate date, String rest, boolean explicit) {
    }

    private static DayPart dayPart(String t, LocalDate today) {
        String s = t.replaceFirst("^(on|this|by)\\s+", "");
        if (s.startsWith("day after tomorrow")) {
            return new DayPart(today.plusDays(2), s.substring(18).trim(), true);
        }
        if (s.startsWith("tomorrow")) {
            return new DayPart(today.plusDays(1), s.substring(8).trim(), true);
        }
        if (s.startsWith("today")) {
            return new DayPart(today, s.substring(5).trim(), true);
        }
        boolean next = s.startsWith("next ");
        String w = next ? s.substring(5) : s;
        for (DayOfWeek dow : DayOfWeek.values()) {
            String name = dow.name().toLowerCase(Locale.ROOT);
            if (w.startsWith(name) || w.matches("^" + name.substring(0, 3) + "\\b.*")) {
                LocalDate date = today.with(TemporalAdjusters.next(dow)); // "Friday" / "next Friday" = the coming Friday
                String rest = w.substring(w.startsWith(name) ? name.length() : 3).trim();
                return new DayPart(date, rest, true);
            }
        }
        Matcher dm = DAY_MONTH.matcher(s);
        if (dm.matches() && month(dm.group(2)) != null) {
            return new DayPart(dateIn(today, month(dm.group(2)), Integer.parseInt(dm.group(1))), dm.group(3).trim(), true);
        }
        Matcher md = MONTH_DAY.matcher(s);
        if (md.matches() && month(md.group(1)) != null) {
            return new DayPart(dateIn(today, month(md.group(1)), Integer.parseInt(md.group(2))), md.group(3).trim(), true);
        }
        return new DayPart(today, s, false);
    }

    private static Month month(String word) {
        for (Month m : Month.values()) {
            String name = m.name().toLowerCase(Locale.ROOT);
            if (word.equals(name) || (word.length() >= 3 && name.startsWith(word))) {
                return m;
            }
        }
        return null;
    }

    /** The next occurrence of that day and month (this year, or next year if it has passed). */
    private static LocalDate dateIn(LocalDate today, Month month, int day) {
        LocalDate d = LocalDate.of(today.getYear(), month, Math.min(day, month.length(today.isLeapYear())));
        return d.isBefore(today) ? d.plusYears(1) : d;
    }
}
