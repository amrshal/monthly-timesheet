package com.amrshalaby.timesheet.timesheet;

import jakarta.inject.Singleton;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Singleton
public final class MonthGridService {
    public MonthGrid build(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstGridDate = yearMonth.atDay(1)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastGridDate = yearMonth.atEndOfMonth()
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<WeekRow> weeks = new ArrayList<>();
        for (LocalDate day = firstGridDate; !day.isAfter(lastGridDate); day = day.plusWeeks(1)) {
            weeks.add(buildWeek(day, month));
        }

        return new MonthGrid(yearMonth, weeks);
    }

    private WeekRow buildWeek(LocalDate monday, int selectedMonth) {
        List<DayCell> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            days.add(new DayCell(date, date.getMonthValue() == selectedMonth));
        }

        return new WeekRow(days);
    }

    public record MonthGrid(YearMonth month, List<WeekRow> weeks) {
    }

    public record WeekRow(List<DayCell> days) {
    }

    public record DayCell(LocalDate date, boolean inMonth) {
    }
}
