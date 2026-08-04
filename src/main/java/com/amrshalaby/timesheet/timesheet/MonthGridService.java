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
        YearMonth ym = YearMonth.of(year, month);
        LocalDate first = ym.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate last = ym.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<WeekRow> weeks = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusWeeks(1)) {
            List<DayCell> days = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate date = day.plusDays(i);
                days.add(new DayCell(date, date.getMonthValue() == month));
            }
            weeks.add(new WeekRow(days));
        }
        return new MonthGrid(ym, weeks);
    }
    public record MonthGrid(YearMonth month, List<WeekRow> weeks) {}
    public record WeekRow(List<DayCell> days) {}
    public record DayCell(LocalDate date, boolean inMonth) {}
}
