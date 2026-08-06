package com.amrshalaby.timesheet.web;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.views.ModelAndView;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.NoSuchElementException;

@Singleton
@Produces
public class NotFoundExceptionHandler implements ExceptionHandler<NoSuchElementException, HttpResponse<ModelAndView<Map<String, Object>>>> {
    @Override
    public HttpResponse<ModelAndView<Map<String, Object>>> handle(HttpRequest request, NoSuchElementException exception) {
        return HttpResponse.status(HttpStatus.NOT_FOUND)
            .body(new ModelAndView<>("error", Map.of(
                "title", "Not found",
                "message", "The requested page or record could not be found."
            )));
    }
}
