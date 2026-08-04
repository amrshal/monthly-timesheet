package com.amrshalaby.timesheet.timesheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonthGridServiceTest {
    private final MonthGridService service = new MonthGridService();

    @Test
    void buildsCompleteMondayToSundayRows() {
        MonthGridService.MonthGrid grid = service.build(2026, 8);

        assertEquals(DayOfWeek.MONDAY, grid.weeks().getFirst().days().getFirst().date().getDayOfWeek());
        assertEquals(DayOfWeek.SUNDAY, grid.weeks().getLast().days().getLast().date().getDayOfWeek());
        assertFalse(grid.weeks().getFirst().days().getFirst().inMonth());
    }

    @Test
    void totalsIgnoreOutsideMonthCells() {
        MonthGridService.MonthGrid grid = service.build(2026, 8);
        var week = grid.weeks().getFirst().days();

        assertEquals(
            60,
            TimesheetTotals.weeklyTotal(
                week,
                Map.of(
                    LocalDate.of(2026, 7, 27), 999,
                    LocalDate.of(2026, 8, 1), 60
                )
            )
        );
    }
}
