package com.amrshalaby.timesheet.web;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.views.ModelAndView;
import jakarta.inject.Singleton;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Produces
public class UnexpectedExceptionHandler implements ExceptionHandler<RuntimeException, HttpResponse<ModelAndView<Map<String, Object>>>> {
    private static final Logger LOG = LoggerFactory.getLogger(UnexpectedExceptionHandler.class);

    @Override
    public HttpResponse<ModelAndView<Map<String, Object>>> handle(HttpRequest request, RuntimeException exception) {
        LOG.error(
            "Unexpected application error while handling {} {}: {}",
            request.getMethod(),
            request.getPath(),
            exception.getClass().getName()
        );
        return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ModelAndView<>("error", Map.of(
                "title", "Something went wrong",
                "message", "An unexpected problem occurred. Please try again later."
            )));
    }
}
