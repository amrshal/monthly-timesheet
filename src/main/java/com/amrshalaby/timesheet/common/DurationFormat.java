package com.amrshalaby.timesheet.common;

import java.util.OptionalInt;
import java.util.regex.Pattern;

public final class DurationFormat {
    private static final Pattern INTEGER = Pattern.compile("\\d+");
    private static final Pattern HH_MM = Pattern.compile("\\d{1,2}:\\d{1,2}");
    private DurationFormat() {}

    public static OptionalInt parse(String text) {
        if (text == null || text.isBlank()) return OptionalInt.empty();
        String value = text.trim();
        int minutes;
        if (INTEGER.matcher(value).matches()) {
            minutes = Integer.parseInt(value) * 60;
        } else if (HH_MM.matcher(value).matches()) {
            String[] parts = value.split(":", -1);
            int hours = Integer.parseInt(parts[0]);
            int mins = Integer.parseInt(parts[1]);
            if (mins > 59) throw new IllegalArgumentException("Minutes must be from 00 to 59.");
            minutes = hours * 60 + mins;
        } else {
            throw new IllegalArgumentException("Enter a duration from 00:00 to 24:00.");
        }
        if (minutes < 0 || minutes > 1440) throw new IllegalArgumentException("Enter a duration from 00:00 to 24:00.");
        return OptionalInt.of(minutes);
    }

    public static String format(Integer minutes) {
        if (minutes == null) return "";
        if (minutes < 0 || minutes > 1440) throw new IllegalArgumentException("minutes out of range");
        return "%02d:%02d".formatted(minutes / 60, minutes % 60);
    }
}
