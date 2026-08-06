package com.amrshalaby.timesheet.security;

import static com.amrshalaby.timesheet.security.SecurityTestFixtures.ROLE_HEADER;
import static com.amrshalaby.timesheet.security.SecurityTestFixtures.USER_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amrshalaby.timesheet.audit.AuditEventType;
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
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest(
    transactional = false,
    environments = "security-http",
    deduceEnvironment = false
)
@Property(name = "micronaut.security.enabled", value = "true")
@Property(name = "micronaut.security.csrf.enabled", value = "false")
@Property(name = "micronaut.http.client.follow-redirects", value = "false")
final class SecurityHttpTest {
    private static final long EMPLOYEE_SUBMITTED = 10L;
    private static final long EMPLOYEE_APPROVED = 11L;
    private static final long EMPLOYEE_DRAFT = 12L;
    private static final long OTHER_SUBMITTED = 20L;

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
        users.reset(
            users.user(1L, "employee@example.com", "Employee", UserRole.EMPLOYEE, 2L, true),
            users.user(2L, "manager@example.com", "Manager", UserRole.MANAGER, null, true),
            users.user(3L, "admin@example.com", "Admin", UserRole.ADMIN, null, true),
            users.user(4L, "other@example.com", "Other", UserRole.EMPLOYEE, 99L, true)
        );
        timesheets.reset(
            timesheets.timesheet(EMPLOYEE_SUBMITTED, 1L, 2026, 8, TimesheetStatus.SUBMITTED),
            timesheets.timesheet(EMPLOYEE_APPROVED, 1L, 2026, 9, TimesheetStatus.APPROVED),
            timesheets.timesheet(EMPLOYEE_DRAFT, 1L, 2026, 10, TimesheetStatus.DRAFT),
            timesheets.timesheet(OTHER_SUBMITTED, 4L, 2026, 8, TimesheetStatus.SUBMITTED)
        );
        entries.reset(
            entries.entry(1L, EMPLOYEE_SUBMITTED, LocalDate.of(2026, 8, 5), 480, "submitted note"),
            entries.entry(2L, EMPLOYEE_APPROVED, LocalDate.of(2026, 9, 5), 480, "approved note")
        );
        auditEvents.reset();
    }

    @Test
    void anonymousUsersCannotOpenProtectedTimesheets() {
        HttpClientResponseException exception = assertResponseException(HttpRequest.GET("/timesheets"));

        assertTrue(
            exception.getStatus() == HttpStatus.UNAUTHORIZED || exception.getStatus() == HttpStatus.SEE_OTHER,
            "Expected anonymous request to be rejected or redirected"
        );
    }

    @Test
    void employeeCannotUseManagerRouteToViewAnotherEmployeeTimesheet() {
        HttpClientResponseException exception = assertResponseException(
            authenticated(HttpRequest.GET("/manager/timesheets/" + EMPLOYEE_SUBMITTED), "employee@example.com", "EMPLOYEE")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertFalse(responseBody(exception).contains("SecurityException"));
        assertFalse(responseBody(exception).contains("You are not authorised to access this employee."));
    }

    @Test
    void missingRecordsReturnSafeNotFoundPage() {
        HttpClientResponseException exception = assertResponseException(
            authenticated(HttpRequest.GET("/manager/timesheets/99999"), "manager@example.com", "MANAGER")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertTrue(responseBody(exception).contains("The requested page or record could not be found."));
        assertFalse(responseBody(exception).contains("NoSuchElementException"));
    }

    @Test
    void unexpectedErrorsReturnGenericProductionSafePage() {
        HttpClientResponseException exception = assertResponseException(
            authenticated(HttpRequest.GET("/test-errors/unexpected"), "employee@example.com", "EMPLOYEE")
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
        assertTrue(responseBody(exception).contains("An unexpected problem occurred. Please try again later."));
        assertFalse(responseBody(exception).contains("internal password-like detail"));
        assertFalse(responseBody(exception).contains("IllegalStateException"));
    }

    @Test
    void employeeCannotEditSubmittedTimesheet() {
        HttpClientResponseException exception = assertResponseException(
            authenticated(
                HttpRequest.POST("/timesheets/2026/8", Map.of(
                    "expectedVersion", "1",
                    "duration_2026-08-05", "07:30",
                    "note_2026-08-05", "changed"
                )).contentType(MediaType.APPLICATION_JSON_TYPE),
                "employee@example.com",
                "EMPLOYEE"
            )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void employeeCannotSubmitAnotherUsersDraftViaManagerRoute() {
        HttpClientResponseException exception = assertResponseException(
            authenticated(
                HttpRequest.POST("/manager/timesheets/" + EMPLOYEE_DRAFT + "/submit", Map.of("expectedVersion", "1"))
                    .contentType(MediaType.APPLICATION_JSON_TYPE),
                "employee@example.com",
                "EMPLOYEE"
            )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void managerCannotAccessUnassignedEmployeeTimesheet() {
        HttpClientResponseException exception = assertResponseException(
            authenticated(HttpRequest.GET("/manager/timesheets/" + OTHER_SUBMITTED), "manager@example.com", "MANAGER")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void managerCannotReopenApprovedTimesheet() {
        HttpClientResponseException exception = assertResponseException(
            authenticated(
                HttpRequest.POST("/manager/timesheets/" + EMPLOYEE_APPROVED + "/reopen", Map.of(
                    "expectedVersion", "1",
                    "reason", "manager should not reopen approved"
                )).contentType(MediaType.APPLICATION_JSON_TYPE),
                "manager@example.com",
                "MANAGER"
            )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void manipulatedStatusDoesNotAllowApprovingDraftTimesheet() {
        HttpClientResponseException exception = assertResponseException(
            authenticated(
                HttpRequest.POST("/manager/timesheets/" + EMPLOYEE_DRAFT + "/approve", Map.of("expectedVersion", "1"))
                    .contentType(MediaType.APPLICATION_JSON_TYPE),
                "manager@example.com",
                "MANAGER"
            )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void assignedManagerCanPrivilegedEditSubmittedTimesheetAndAuditIsWritten() {
        HttpResponse<String> response = client.toBlocking().exchange(
            authenticated(
                HttpRequest.POST("/manager/timesheets/" + EMPLOYEE_SUBMITTED, Map.of(
                    "expectedVersion", "1",
                    "duration_2026-08-05", "07:30",
                    "note_2026-08-05", "manager correction"
                )).contentType(MediaType.APPLICATION_JSON_TYPE),
                "manager@example.com",
                "MANAGER"
            ),
            String.class
        );

        assertEquals(HttpStatus.SEE_OTHER, response.getStatus());
        assertEquals(TimesheetStatus.SUBMITTED, timesheets.findById(EMPLOYEE_SUBMITTED).orElseThrow().getStatus());
        assertTrue(auditEvents.events().stream()
            .anyMatch(event -> AuditEventType.TIMESHEET_PRIVILEGED_EDITED.name().equals(event.getEventType())));
    }

    @Test
    void administratorCanReopenApprovedTimesheet() {
        HttpResponse<String> response = client.toBlocking().exchange(
            authenticated(
                HttpRequest.POST("/admin/timesheets/" + EMPLOYEE_APPROVED + "/reopen", Map.of(
                    "expectedVersion", "1",
                    "reason", "admin correction"
                )).contentType(MediaType.APPLICATION_JSON_TYPE),
                "admin@example.com",
                "ADMIN"
            ),
            String.class
        );

        assertEquals(HttpStatus.SEE_OTHER, response.getStatus());
        assertEquals(TimesheetStatus.DRAFT, timesheets.findById(EMPLOYEE_APPROVED).orElseThrow().getStatus());
    }

    private HttpClientResponseException assertResponseException(HttpRequest<?> request) {
        try {
            client.toBlocking().exchange(request, String.class);
        } catch (HttpClientResponseException exception) {
            return exception;
        }
        throw new AssertionError("Expected request to fail");
    }

    private HttpRequest<?> authenticated(MutableHttpRequest<?> request, String email, String roles) {
        return request.header(USER_HEADER, email).header(ROLE_HEADER, roles);
    }

    private String responseBody(HttpClientResponseException exception) {
        return exception.getResponse().getBody(String.class).orElse("");
    }
}
