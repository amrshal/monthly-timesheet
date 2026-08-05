package com.amrshalaby.timesheet.timesheet;

import java.time.LocalDate;

public record DailyEntryCommand(
    LocalDate workDate,
    String durationText,
    String note
) {
}
