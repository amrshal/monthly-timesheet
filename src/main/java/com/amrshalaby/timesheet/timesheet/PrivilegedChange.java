package com.amrshalaby.timesheet.timesheet;

import java.time.LocalDate;

public record PrivilegedChange(
    LocalDate workDate,
    Integer durationBefore,
    Integer durationAfter,
    String noteBefore,
    String noteAfter
) {
}
