package com.amrshalaby.timesheet.timesheet;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class TimesheetTotals {
    private TimesheetTotals() {
    }

    public static int monthlyTotal(Map<LocalDate, Integer> entries) {
        return entries.values().stream()
            .mapToInt(Integer::intValue)
            .sum();
    }

    public static int weeklyTotal(
        List<MonthGridService.DayCell> days,
        Map<LocalDate, Integer> entries
    ) {
        return days.stream()
            .filter(MonthGridService.DayCell::inMonth)
            .map(MonthGridService.DayCell::date)
            .map(entries::get)
            .filter(value -> value != null)
            .mapToInt(Integer::intValue)
            .sum();
    }
}
