package com.amrshalaby.timesheet;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/health")
public final class HealthController {
    @Get
    public String index() {
        return "Monthly Timesheet is running";
    }
}
