package com.amrshalaby.timesheet.admin;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.views.View;
import java.util.Map;

@Controller("/admin")
@Secured("ADMIN")
public class AdminController {
    @Get("/users")
    @View("dashboard")
    public Map<String, Object> users() {
        return Map.of(
            "title", "User administration",
            "message", "Administrators and authorised managers create users here."
        );
    }

    @Get("/audit")
    @View("dashboard")
    public Map<String, Object> audit() {
        return Map.of(
            "title", "Audit log",
            "message", "Immutable audit events are retained in the database."
        );
    }
}
