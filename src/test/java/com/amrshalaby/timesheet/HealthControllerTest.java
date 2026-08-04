package com.amrshalaby.timesheet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
final class HealthControllerTest {
    @Inject
    @Client("/")
    BlockingHttpClient client;

    @Test
    void healthEndpointReturnsStatusMessage() {
        String response = client.retrieve(HttpRequest.GET("/health"));

        assertEquals("Monthly Timesheet is running", response);
    }
}
