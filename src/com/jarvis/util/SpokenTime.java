package com.jarvis.util;

import java.time.LocalTime;

/**
 * Formats a time the way a person says it, with hours, minutes and seconds:
 * "5 o'clock, 50 minutes and 46 seconds in the evening".
 */
public final class SpokenTime {

    private SpokenTime() {
    }

    public static String time(LocalTime t) {
        int h = t.getHour();
        int m = t.getMinute();
        int s = t.getSecond();
        int h12 = h % 12 == 0 ? 12 : h % 12;
        StringBuilder sb = new StringBuilder().append(h12).append(" o'clock");
        if (m > 0 || s > 0) {
            sb.append(", ").append(m).append(m == 1 ? " minute" : " minutes");
            sb.append(" and ").append(s).append(s == 1 ? " second" : " seconds");
        }
        return sb.append(' ').append(partOfDay(h)).toString();
    }

    private static String partOfDay(int h) {
        if (h < 5) {
            return "at night";
        }
        if (h < 12) {
            return "in the morning";
        }
        if (h < 17) {
            return "in the afternoon";
        }
        if (h < 21) {
            return "in the evening";
        }
        return "at night";
    }
}
