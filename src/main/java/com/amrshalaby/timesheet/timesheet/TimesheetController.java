package com.amrshalaby.timesheet.timesheet;

import com.amrshalaby.timesheet.audit.AuditService;
import com.amrshalaby.timesheet.auth.CurrentUserService;
import com.amrshalaby.timesheet.common.DurationFormat;
import com.amrshalaby.timesheet.user.AppUser;
import com.amrshalaby.timesheet.user.UserService;
import com.amrshalaby.timesheet.web.ViewModel;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.context.annotation.Value;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.session.Session;
import io.micronaut.views.ModelAndView;
import io.micronaut.views.View;
import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller("/timesheets")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class TimesheetController {
    private final CurrentUserService currentUserService;
    private final MonthGridService monthGridService;
    private final TimesheetService timesheetService;
    private final UserService userService;
    private final AuditService auditService;
    private final ZoneId businessZone;

    public TimesheetController(
        CurrentUserService currentUserService,
        MonthGridService monthGridService,
        TimesheetService timesheetService,
        UserService userService,
        AuditService auditService,
        @Value("${app.timezone:Europe/London}") String businessTimezone
    ) {
        this.currentUserService = currentUserService;
        this.monthGridService = monthGridService;
        this.timesheetService = timesheetService;
        this.userService = userService;
        this.auditService = auditService;
        this.businessZone = ZoneId.of(businessTimezone);
    }

    @Get
    @View("timesheet")
    public Map<String, Object> current(Principal principal, Session session) {
        YearMonth now = YearMonth.now(businessZone);
        return view(principal, now.getYear(), now.getMonthValue(), session);
    }

    @Get("/{year}/{month}")
    @View("timesheet")
    public Map<String, Object> view(Principal principal, int year, int month, Session session) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
        MonthlyTimesheet timesheet = timesheetService.getOrCreate(actor, actor, year, month);
        return timesheetModel(actor, actor, timesheet, session);
    }

    @Post("/{year}/{month}")
    public HttpResponse<?> save(
        Principal principal,
        int year,
        int month,
        @Body Map<String, String> formValues,
        Session session
    ) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
        MonthlyTimesheet timesheet = timesheetService.getOrCreate(actor, actor, year, month);
        try {
            timesheetService.saveEmployeeDraft(actor, actor, timesheet, formCommand(year, month, formValues));
        } catch (TimesheetValidationException exception) {
            return HttpResponse.badRequest(new ModelAndView<>(
                "timesheet",
                timesheetModel(actor, actor, timesheet, session, exception.command(), exception.fieldErrors())
            ));
        }
        return HttpResponse.seeOther(URI.create("/timesheets/" + year + "/" + month));
    }

    @Post("/{year}/{month}/submit")
    public HttpResponse<?> submit(Principal principal, int year, int month) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
        MonthlyTimesheet timesheet = timesheetService.getOrCreate(actor, actor, year, month);
        timesheetService.submit(actor, actor, timesheet);
        return HttpResponse.seeOther(URI.create("/timesheets/" + year + "/" + month));
    }

    public Map<String, Object> timesheetModel(AppUser actor, AppUser subject, MonthlyTimesheet timesheet, Session session) {
        return timesheetModel(actor, subject, timesheet, session, null, Map.of());
    }

    public Map<String, Object> timesheetModel(
        AppUser actor,
        AppUser subject,
        MonthlyTimesheet timesheet,
        Session session,
        SaveTimesheetCommand submittedCommand,
        Map<String, String> fieldErrors
    ) {
        MonthGridService.MonthGrid grid = monthGridService.build(timesheet.getYear(), timesheet.getMonth());
        Map<LocalDate, DailyTimeEntry> entriesByDate = new HashMap<>();
        Map<LocalDate, String> durationByDate = new HashMap<>();
        Map<LocalDate, String> noteByDate = new HashMap<>();
        for (DailyTimeEntry entry : timesheetService.entries(timesheet.getId())) {
            entriesByDate.put(entry.getWorkDate(), entry);
            durationByDate.put(entry.getWorkDate(), DurationFormat.format(entry.getDurationMinutes()));
            noteByDate.put(entry.getWorkDate(), entry.getNote());
        }
        if (submittedCommand != null) {
            for (DailyEntryCommand entry : submittedCommand.entries()) {
                durationByDate.put(entry.workDate(), entry.durationText());
                noteByDate.put(entry.workDate(), entry.note());
            }
        }

        Map<LocalDate, Integer> minutesByDate = new HashMap<>();
        entriesByDate.forEach((date, entry) -> minutesByDate.put(date, entry.getDurationMinutes()));

        List<String> weeklyTotals = new ArrayList<>();
        for (MonthGridService.WeekRow week : grid.weeks()) {
            weeklyTotals.add(DurationFormat.format(TimesheetTotals.weeklyTotal(week.days(), minutesByDate)));
        }

        boolean self = actor.getId().equals(subject.getId());
        boolean privileged = actor.getRole() == com.amrshalaby.timesheet.user.UserRole.MANAGER
            || actor.getRole() == com.amrshalaby.timesheet.user.UserRole.ADMIN;

        String baseAction = actionBase(actor, subject, timesheet);

        return ViewModel.withCsrf(Map.ofEntries(
            Map.entry("title", "Timesheet"),
            Map.entry("actor", actor),
            Map.entry("subject", subject),
            Map.entry("timesheet", timesheet),
            Map.entry("expectedVersion", timesheet.getVersion()),
            Map.entry("statusLabel", statusLabel(timesheet.getStatus())),
            Map.entry("grid", grid),
            Map.entry("entries", entriesByDate),
            Map.entry("durations", durationByDate),
            Map.entry("notes", noteByDate),
            Map.entry("fieldErrors", fieldErrors),
            Map.entry("weeklyTotals", weeklyTotals),
            Map.entry("monthlyTotal", DurationFormat.format(monthlyTotal(entriesByDate))),
            Map.entry("recordedDays", entriesByDate.size()),
            Map.entry("auditEvents", auditService.findForTimesheet(subject.getId(), timesheet.getId())),
            Map.entry("canEdit", StatusTransitionPolicy.employeeCanEdit(timesheet.getStatus(), self) || privileged),
            Map.entry("canSubmit", StatusTransitionPolicy.canSubmit(timesheet.getStatus(), actor.getRole(), true)),
            Map.entry("canApprove", StatusTransitionPolicy.canApprove(timesheet.getStatus(), actor.getRole(), true)),
            Map.entry("canReopen", StatusTransitionPolicy.canReopen(timesheet.getStatus(), actor.getRole(), true)),
            Map.entry("saveAction", baseAction),
            Map.entry("submitAction", baseAction + "/submit"),
            Map.entry("approveAction", baseAction + "/approve"),
            Map.entry("reopenAction", baseAction + "/reopen")
        ), session);
    }

    public SaveTimesheetCommand formCommand(int year, int month, Map<String, String> formValues) {
        List<DailyEntryCommand> entries = YearMonth.of(year, month).atDay(1)
            .datesUntil(YearMonth.of(year, month).plusMonths(1).atDay(1))
            .map(date -> new DailyEntryCommand(
                date,
                formValues.getOrDefault("duration_" + date, ""),
                formValues.getOrDefault("note_" + date, "")
            ))
            .toList();
        return new SaveTimesheetCommand(year, month, parseExpectedVersion(formValues.get("expectedVersion")), entries);
    }


    private String actionBase(AppUser actor, AppUser subject, MonthlyTimesheet timesheet) {
        if (actor.getRole() == com.amrshalaby.timesheet.user.UserRole.ADMIN && !actor.getId().equals(subject.getId())) {
            return "/admin/timesheets/" + timesheet.getId();
        }
        if (actor.getRole() == com.amrshalaby.timesheet.user.UserRole.MANAGER && !actor.getId().equals(subject.getId())) {
            return "/manager/timesheets/" + timesheet.getId();
        }

        return "/timesheets/" + timesheet.getYear() + "/" + timesheet.getMonth();
    }

    private int monthlyTotal(Map<LocalDate, DailyTimeEntry> entriesByDate) {
        return entriesByDate.values().stream()
            .mapToInt(DailyTimeEntry::getDurationMinutes)
            .sum();
    }

    private Long parseExpectedVersion(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Long.valueOf(value);
    }

    private String statusLabel(TimesheetStatus status) {
        return switch (status) {
            case DRAFT -> "Draft";
            case SUBMITTED -> "Submitted";
            case APPROVED -> "Approved";
        };
    }
}
