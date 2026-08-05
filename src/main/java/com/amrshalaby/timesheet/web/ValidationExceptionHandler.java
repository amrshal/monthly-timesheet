package com.amrshalaby.timesheet.web;

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
public class ValidationExceptionHandler implements ExceptionHandler<IllegalArgumentException, HttpResponse<ModelAndView<Map<String, Object>>>> {
    @Override
    public HttpResponse<ModelAndView<Map<String, Object>>> handle(HttpRequest request, IllegalArgumentException exception) {
        return HttpResponse.status(HttpStatus.BAD_REQUEST)
            .body(new ModelAndView<>("error", Map.of(
                "title", "Check your input",
                "message", exception.getMessage()
            )));
    }
}
