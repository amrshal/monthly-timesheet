package com.amrshalaby.timesheet.web;

import com.amrshalaby.timesheet.timesheet.TimesheetConflictException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.views.ModelAndView;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
@Produces
public class TimesheetConflictExceptionHandler implements ExceptionHandler<TimesheetConflictException, HttpResponse<ModelAndView<Map<String, Object>>>> {
    @Override
    public HttpResponse<ModelAndView<Map<String, Object>>> handle(HttpRequest request, TimesheetConflictException exception) {
        return HttpResponse.status(HttpStatus.CONFLICT)
            .body(new ModelAndView<>("error", Map.of(
                "title", "Timesheet changed",
                "message", exception.getMessage()
            )));
    }
}
