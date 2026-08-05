package com.amrshalaby.timesheet.timesheet;

import java.util.List;

public record SaveTimesheetCommand(
    int year,
    int month,
    Long expectedVersion,
    List<DailyEntryCommand> entries
) {
}
