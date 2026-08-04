package com.amrshalaby.timesheet.timesheet;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.views.View;
import java.time.YearMonth;
import java.util.Map;

@Controller("/timesheets")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class TimesheetController {
    private final MonthGridService monthGridService;

    public TimesheetController(MonthGridService monthGridService) {
        this.monthGridService = monthGridService;
    }

    @Get
    @View("timesheet")
    public Map<String, Object> current() {
        YearMonth now = YearMonth.now();
        return view(now.getYear(), now.getMonthValue());
    }

    @Get("/{year}/{month}")
    @View("timesheet")
    public Map<String, Object> view(int year, int month) {
        MonthGridService.MonthGrid grid = monthGridService.build(year, month);

        return Map.of(
            "title", "Timesheet",
            "grid", grid,
            "status", TimesheetStatus.DRAFT,
            "entries", Map.of(),
            "monthlyTotal", "00:00"
        );
    }
}
