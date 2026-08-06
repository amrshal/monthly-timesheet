package com.amrshalaby.timesheet.security;

import static com.amrshalaby.timesheet.security.SecurityTestFixtures.ROLE_HEADER;
import static com.amrshalaby.timesheet.security.SecurityTestFixtures.USER_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amrshalaby.timesheet.timesheet.TimesheetStatus;
import com.amrshalaby.timesheet.user.UserRole;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest(
    transactional = false,
    environments = "security-csrf",
    deduceEnvironment = false
)
@Property(name = "micronaut.security.enabled", value = "true")
@Property(name = "micronaut.security.csrf.enabled", value = "true")
@Property(name = "micronaut.http.client.follow-redirects", value = "false")
final class CsrfSecurityHttpTest {
    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    TestUserRepository users;

    @Inject
    TestMonthlyTimesheetRepository timesheets;

    @Inject
    TestDailyTimeEntryRepository entries;

    @Inject
    TestAuditEventRepository auditEvents;

    @BeforeEach
    void setUp() {
        users.reset(users.user(1L, "employee@example.com", "Employee", UserRole.EMPLOYEE, null, true));
        timesheets.reset(timesheets.timesheet(1L, 1L, 2026, 8, TimesheetStatus.DRAFT));
        entries.reset();
        auditEvents.reset();
    }

    @Test
    void applicationPostWithoutCsrfTokenIsRejected() {
        HttpClientResponseException exception = assertResponseException(
            authenticated(
                HttpRequest.POST("/timesheets/2026/8", "expectedVersion=1")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
            )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void loginFormIncludesCsrfToken() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/login"), String.class);

        assertEquals(HttpStatus.OK, response.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(response.body().contains("name=\"csrfToken\""));
    }

    private HttpClientResponseException assertResponseException(HttpRequest<?> request) {
        try {
            client.toBlocking().exchange(request, String.class);
        } catch (HttpClientResponseException exception) {
            return exception;
        }
        throw new AssertionError("Expected request to fail");
    }

    private HttpRequest<?> authenticated(MutableHttpRequest<?> request) {
        return request.header(USER_HEADER, "employee@example.com").header(ROLE_HEADER, "EMPLOYEE");
    }
}
