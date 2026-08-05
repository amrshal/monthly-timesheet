package com.amrshalaby.timesheet.web;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.views.View;
import java.util.Map;

@Controller
public class LoginController {
    @Get("/login")
    @View("login")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public Map<String, Object> login() {
        return Map.of("title", "Sign in");
    }
}
