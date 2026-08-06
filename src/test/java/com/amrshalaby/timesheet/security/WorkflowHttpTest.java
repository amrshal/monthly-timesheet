package com.amrshalaby.timesheet.security;

import static com.amrshalaby.timesheet.security.SecurityTestFixtures.ROLE_HEADER;
import static com.amrshalaby.timesheet.security.SecurityTestFixtures.USER_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amrshalaby.timesheet.audit.AuditEventType;
import com.amrshalaby.timesheet.timesheet.DailyTimeEntry;
import com.amrshalaby.timesheet.timesheet.MonthlyTimesheet;
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
final class WorkflowHttpTest {
    private static final long EMPLOYEE_DRAFT = 100L;
    private static final long MANAGER_DRAFT = 110L;
    private static final long SUBMITTED_TO_REOPEN = 120L;
    private static final long APPROVED_TO_REOPEN = 130L;
    private static final long SUBMITTED_TO_EDIT = 140L;
    private static final long APPROVED_TO_EDIT = 150L;

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
            users.user(3L, "admin@example.com", "Admin", UserRole.ADMIN, null, true)
        );
        timesheets.reset(
            timesheets.timesheet(EMPLOYEE_DRAFT, 1L, 2026, 8, TimesheetStatus.DRAFT),
            timesheets.timesheet(MANAGER_DRAFT, 1L, 2026, 9, TimesheetStatus.DRAFT),
            timesheets.timesheet(SUBMITTED_TO_REOPEN, 1L, 2026, 10, TimesheetStatus.SUBMITTED),
            timesheets.timesheet(APPROVED_TO_REOPEN, 1L, 2026, 11, TimesheetStatus.APPROVED),
            timesheets.timesheet(SUBMITTED_TO_EDIT, 1L, 2026, 12, TimesheetStatus.SUBMITTED),
            timesheets.timesheet(APPROVED_TO_EDIT, 1L, 2027, 1, TimesheetStatus.APPROVED)
        );
        entries.reset(
            entries.entry(1L, MANAGER_DRAFT, LocalDate.of(2026, 9, 1), 480, "ready"),
            entries.entry(2L, SUBMITTED_TO_REOPEN, LocalDate.of(2026, 10, 1), 480, "submitted"),
            entries.entry(3L, APPROVED_TO_REOPEN, LocalDate.of(2026, 11, 2), 480, "approved"),
            entries.entry(4L, SUBMITTED_TO_EDIT, LocalDate.of(2026, 12, 3), 480, "before"),
            entries.entry(5L, APPROVED_TO_EDIT, LocalDate.of(2027, 1, 4), 480, "before")
        );
        auditEvents.reset();
    }

    @Test
    void employeeSubmissionAndManagerApprovalWorkflowCompletes() {
        assertSeeOther(postAsEmployee("/timesheets/2026/8", Map.of(
            "expectedVersion", "1",
            "duration_2026-08-05", "07:30",
            "note_2026-08-05", "worked future-facing release tasks"
        )));
        DailyTimeEntry savedEntry = entries.findByTimesheetIdAndWorkDate(EMPLOYEE_DRAFT, LocalDate.of(2026, 8, 5)).orElseThrow();
        assertEquals(450, savedEntry.getDurationMinutes());
        assertEquals("worked future-facing release tasks", savedEntry.getNote());

        assertSeeOther(postAsEmployee("/timesheets/2026/8/submit", Map.of("expectedVersion", "1")));
        assertEquals(TimesheetStatus.SUBMITTED, timesheets.findById(EMPLOYEE_DRAFT).orElseThrow().getStatus());

        HttpClientResponseException editAfterSubmit = assertResponseException(postAsEmployee("/timesheets/2026/8", Map.of(
            "expectedVersion", "2",
            "duration_2026-08-05", "08:00"
        )));
        assertEquals(HttpStatus.FORBIDDEN, editAfterSubmit.getStatus());

        assertSeeOther(postAsManager("/manager/timesheets/" + EMPLOYEE_DRAFT + "/approve", Map.of("expectedVersion", "2")));
        MonthlyTimesheet approved = timesheets.findById(EMPLOYEE_DRAFT).orElseThrow();
        assertEquals(TimesheetStatus.APPROVED, approved.getStatus());
        assertEquals(2L, approved.getApprovedByUserId());
        assertAuditEvent(AuditEventType.TIMESHEET_SUBMITTED, EMPLOYEE_DRAFT, 1L);
        assertAuditEvent(AuditEventType.TIMESHEET_APPROVED, EMPLOYEE_DRAFT, 2L);
    }

    @Test
    void managerSubmitsEmployeeDraftThenApprovesInSeparateOperation() {
        assertSeeOther(postAsManager("/manager/timesheets/" + MANAGER_DRAFT + "/submit", Map.of("expectedVersion", "1")));
        MonthlyTimesheet submitted = timesheets.findById(MANAGER_DRAFT).orElseThrow();
        assertEquals(TimesheetStatus.SUBMITTED, submitted.getStatus());
        assertEquals(2L, submitted.getSubmittedByUserId());

        assertSeeOther(postAsManager("/manager/timesheets/" + MANAGER_DRAFT + "/approve", Map.of("expectedVersion", "2")));
        MonthlyTimesheet approved = timesheets.findById(MANAGER_DRAFT).orElseThrow();
        assertEquals(TimesheetStatus.APPROVED, approved.getStatus());
        assertEquals(2L, approved.getApprovedByUserId());
        assertAuditEvent(AuditEventType.TIMESHEET_SUBMITTED, MANAGER_DRAFT, 2L);
        assertAuditEvent(AuditEventType.TIMESHEET_APPROVED, MANAGER_DRAFT, 2L);
    }

    @Test
    void managerReopensSubmittedTimesheetAndEmployeeCanEditAgain() {
        assertSeeOther(postAsManager("/manager/timesheets/" + SUBMITTED_TO_REOPEN + "/reopen", Map.of(
            "expectedVersion", "1",
            "reason", "Need employee correction"
        )));
        MonthlyTimesheet reopened = timesheets.findById(SUBMITTED_TO_REOPEN).orElseThrow();
        assertEquals(TimesheetStatus.DRAFT, reopened.getStatus());
        assertEquals(null, reopened.getSubmittedAt());
        assertEquals(null, reopened.getSubmittedByUserId());

        assertSeeOther(postAsEmployee("/timesheets/2026/10", Map.of(
            "expectedVersion", "2",
            "duration_2026-10-01", "06:30",
            "note_2026-10-01", "corrected after reopen"
        )));
        DailyTimeEntry corrected = entries.findByTimesheetIdAndWorkDate(SUBMITTED_TO_REOPEN, LocalDate.of(2026, 10, 1)).orElseThrow();
        assertEquals(390, corrected.getDurationMinutes());
        assertAuditEventDetails(AuditEventType.TIMESHEET_REOPENED, SUBMITTED_TO_REOPEN, "Need employee correction");
    }

    @Test
    void onlyAdministratorCanReopenApprovedTimesheet() {
        HttpClientResponseException managerDenied = assertResponseException(postAsManager(
            "/manager/timesheets/" + APPROVED_TO_REOPEN + "/reopen",
            Map.of("expectedVersion", "1", "reason", "Manager attempt")
        ));
        assertEquals(HttpStatus.FORBIDDEN, managerDenied.getStatus());

        assertSeeOther(postAsAdmin("/admin/timesheets/" + APPROVED_TO_REOPEN + "/reopen", Map.of(
            "expectedVersion", "1",
            "reason", "Approved correction needed"
        )));
        MonthlyTimesheet reopened = timesheets.findById(APPROVED_TO_REOPEN).orElseThrow();
        assertEquals(TimesheetStatus.DRAFT, reopened.getStatus());
        assertEquals(null, reopened.getApprovedAt());
        assertEquals(null, reopened.getApprovedByUserId());
        assertAuditEventDetails(AuditEventType.TIMESHEET_REOPENED, APPROVED_TO_REOPEN, "previous_approved_by_user_id");
    }

    @Test
    void silentManagerEditKeepsSubmittedStatusAndAuditsBeforeAfterValues() {
        assertSeeOther(postAsManager("/manager/timesheets/" + SUBMITTED_TO_EDIT, Map.of(
            "expectedVersion", "1",
            "duration_2026-12-03", "07:15",
            "note_2026-12-03", "manager correction"
        )));
        MonthlyTimesheet timesheet = timesheets.findById(SUBMITTED_TO_EDIT).orElseThrow();
        assertEquals(TimesheetStatus.SUBMITTED, timesheet.getStatus());
        DailyTimeEntry changed = entries.findByTimesheetIdAndWorkDate(SUBMITTED_TO_EDIT, LocalDate.of(2026, 12, 3)).orElseThrow();
        assertEquals(435, changed.getDurationMinutes());
        assertAuditEventDetails(AuditEventType.TIMESHEET_PRIVILEGED_EDITED, SUBMITTED_TO_EDIT, "\"duration_before\":\"480\"");
        assertAuditEventDetails(AuditEventType.TIMESHEET_PRIVILEGED_EDITED, SUBMITTED_TO_EDIT, "\"note_after\":\"manager correction\"");
    }

    @Test
    void silentAdministratorEditKeepsApprovedStatusAndAuditsBeforeAfterValues() {
        assertSeeOther(postAsAdmin("/admin/timesheets/" + APPROVED_TO_EDIT, Map.of(
            "expectedVersion", "1",
            "duration_2027-01-04", "08:15",
            "note_2027-01-04", "admin correction"
        )));
        MonthlyTimesheet timesheet = timesheets.findById(APPROVED_TO_EDIT).orElseThrow();
        assertEquals(TimesheetStatus.APPROVED, timesheet.getStatus());
        DailyTimeEntry changed = entries.findByTimesheetIdAndWorkDate(APPROVED_TO_EDIT, LocalDate.of(2027, 1, 4)).orElseThrow();
        assertEquals(495, changed.getDurationMinutes());
        assertAuditEventDetails(AuditEventType.TIMESHEET_PRIVILEGED_EDITED, APPROVED_TO_EDIT, "\"duration_after\":\"495\"");
        assertAuditEventDetails(AuditEventType.TIMESHEET_PRIVILEGED_EDITED, APPROVED_TO_EDIT, "\"note_before\":\"before\"");
    }

    @Test
    void employeeCanCreateAndSaveFutureMonthEntry() {
        assertOk(authenticated(HttpRequest.GET("/timesheets/2028/2"), "employee@example.com", "EMPLOYEE"));
        MonthlyTimesheet future = timesheets.findByUserIdAndYearAndMonth(1L, 2028, 2).orElseThrow();
        assertNotNull(future.getId());
        assertEquals(TimesheetStatus.DRAFT, future.getStatus());

        assertSeeOther(postAsEmployee("/timesheets/2028/2", Map.of(
            "expectedVersion", "1",
            "duration_2028-02-29", "08:00",
            "note_2028-02-29", "leap day future entry"
        )));
        DailyTimeEntry futureEntry = entries.findByTimesheetIdAndWorkDate(future.getId(), LocalDate.of(2028, 2, 29)).orElseThrow();
        assertEquals(480, futureEntry.getDurationMinutes());
        assertEquals("leap day future entry", futureEntry.getNote());
    }

    private HttpRequest<?> postAsEmployee(String path, Map<String, String> body) {
        return authenticated(jsonPost(path, body), "employee@example.com", "EMPLOYEE");
    }

    private HttpRequest<?> postAsManager(String path, Map<String, String> body) {
        return authenticated(jsonPost(path, body), "manager@example.com", "MANAGER");
    }

    private HttpRequest<?> postAsAdmin(String path, Map<String, String> body) {
        return authenticated(jsonPost(path, body), "admin@example.com", "ADMIN");
    }

    private MutableHttpRequest<?> jsonPost(String path, Map<String, String> body) {
        return HttpRequest.POST(path, body).contentType(MediaType.APPLICATION_JSON_TYPE);
    }

    private void assertSeeOther(HttpRequest<?> request) {
        HttpResponse<String> response = client.toBlocking().exchange(request, String.class);
        assertEquals(HttpStatus.SEE_OTHER, response.getStatus());
    }

    private void assertOk(HttpRequest<?> request) {
        HttpResponse<String> response = client.toBlocking().exchange(request, String.class);
        assertEquals(HttpStatus.OK, response.getStatus());
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

    private void assertAuditEvent(AuditEventType type, Long entityId, Long actorUserId) {
        assertTrue(auditEvents.events().stream().anyMatch(event ->
            type.name().equals(event.getEventType())
                && entityId.equals(event.getEntityId())
                && actorUserId.equals(event.getActorUserId())
        ));
    }

    private void assertAuditEventDetails(AuditEventType type, Long entityId, String detailFragment) {
        assertTrue(auditEvents.events().stream().anyMatch(event ->
            type.name().equals(event.getEventType())
                && entityId.equals(event.getEntityId())
                && event.getDetailsJson() != null
                && event.getDetailsJson().contains(detailFragment)
        ));
    }
}
