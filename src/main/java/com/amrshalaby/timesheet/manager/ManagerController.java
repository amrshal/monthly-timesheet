package com.amrshalaby.timesheet.manager;

import com.amrshalaby.timesheet.auth.CurrentUserService;
import com.amrshalaby.timesheet.common.BusinessTimeFormatter;
import com.amrshalaby.timesheet.common.DurationFormat;
import com.amrshalaby.timesheet.timesheet.DailyTimeEntry;
import com.amrshalaby.timesheet.timesheet.DailyTimeEntryRepository;
import com.amrshalaby.timesheet.timesheet.MonthlyTimesheet;
import com.amrshalaby.timesheet.timesheet.MonthlyTimesheetRepository;
import com.amrshalaby.timesheet.timesheet.TimesheetController;
import com.amrshalaby.timesheet.timesheet.TimesheetService;
import com.amrshalaby.timesheet.timesheet.TimesheetStatus;
import com.amrshalaby.timesheet.timesheet.TimesheetValidationException;
import com.amrshalaby.timesheet.user.AppUser;
import com.amrshalaby.timesheet.user.AuthorisationService;
import com.amrshalaby.timesheet.user.UserRepository;
import com.amrshalaby.timesheet.user.UserRole;
import com.amrshalaby.timesheet.user.UserService;
import com.amrshalaby.timesheet.web.ViewModel;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.session.Session;
import io.micronaut.views.ModelAndView;
import io.micronaut.views.View;
import java.net.URI;
import java.security.Principal;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller("/manager")
@Secured({"MANAGER", "ADMIN"})
public class ManagerController {
    private final CurrentUserService currentUserService;
    private final AuthorisationService authorisationService;
    private final MonthlyTimesheetRepository timesheetRepository;
    private final DailyTimeEntryRepository entryRepository;
    private final TimesheetService timesheetService;
    private final TimesheetController timesheetController;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ZoneId businessZone;
    private final BusinessTimeFormatter timeFormatter;

    public ManagerController(
        CurrentUserService currentUserService,
        AuthorisationService authorisationService,
        MonthlyTimesheetRepository timesheetRepository,
        DailyTimeEntryRepository entryRepository,
        TimesheetService timesheetService,
        TimesheetController timesheetController,
        UserRepository userRepository,
        UserService userService,
        BusinessTimeFormatter timeFormatter,
        @Value("${app.timezone:Europe/London}") String businessTimezone
    ) {
        this.currentUserService = currentUserService;
        this.authorisationService = authorisationService;
        this.timesheetRepository = timesheetRepository;
        this.entryRepository = entryRepository;
        this.timesheetService = timesheetService;
        this.timesheetController = timesheetController;
        this.userRepository = userRepository;
        this.userService = userService;
        this.timeFormatter = timeFormatter;
        this.businessZone = ZoneId.of(businessTimezone);
    }

    @Get
    @View("manager-dashboard")
    public Map<String, Object> dashboard(Principal principal, Session session) {
        return dashboardModel(principal, null, null, null, null, session);
    }

    @Get("/timesheets")
    @View("manager-dashboard")
    public Map<String, Object> timesheets(
        Principal principal,
        @Nullable Long employeeId,
        @Nullable Integer year,
        @Nullable Integer month,
        @Nullable TimesheetStatus status,
        Session session
    ) {
        return dashboardModel(principal, employeeId, year, month, status, session);
    }

    private Map<String, Object> dashboardModel(
        Principal principal,
        Long employeeId,
        Integer year,
        Integer month,
        TimesheetStatus status,
        Session session
    ) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
        List<AppUser> employees = userRepository.findByManagerId(actor.getId());
        Map<Long, AppUser> employeesById = employees.stream()
            .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        List<Long> scopedEmployeeIds = employees.stream().map(AppUser::getId).toList();
        List<MonthlyTimesheet> submittedTimesheets = scopedEmployeeIds.isEmpty()
            ? List.of()
            : timesheetRepository.findSubmittedForUsers(scopedEmployeeIds);
        List<MonthlyTimesheet> filteredTimesheets = scopedEmployeeIds.isEmpty()
            ? List.of()
            : timesheetRepository.findManaged(scopedEmployeeIds, employeeId, year, month, status);
        Map<Long, Integer> totalMinutesByTimesheetId = totalsByTimesheetId(submittedTimesheets, filteredTimesheets);

        List<ReviewTimesheet> awaitingApproval = submittedTimesheets.stream()
            .map(timesheet -> new ReviewTimesheet(
                timesheet,
                employeesById.get(timesheet.getUserId()),
                submittedBy(timesheet),
                timeFormatter.format(timesheet.getSubmittedAt()),
                DurationFormat.format(totalMinutesByTimesheetId.getOrDefault(timesheet.getId(), 0))
            ))
            .toList();
        List<ManagedTimesheet> managedTimesheets = filteredTimesheets.stream()
            .map(timesheet -> new ManagedTimesheet(
                timesheet,
                employeesById.get(timesheet.getUserId()),
                DurationFormat.format(totalMinutesByTimesheetId.getOrDefault(timesheet.getId(), 0))
            ))
            .toList();

        YearMonth currentMonth = YearMonth.now(businessZone);
        return ViewModel.withCsrf(Map.ofEntries(
            Map.entry("title", "Manager dashboard"),
            Map.entry("employees", employees),
            Map.entry("awaitingApproval", awaitingApproval),
            Map.entry("managedTimesheets", managedTimesheets),
            Map.entry("statuses", TimesheetStatus.values()),
            Map.entry("selectedEmployeeId", employeeId == null ? "" : employeeId),
            Map.entry("selectedYear", year == null ? "" : year),
            Map.entry("selectedMonth", month == null ? "" : month),
            Map.entry("selectedStatus", status == null ? "" : status.name()),
            Map.entry("currentMonth", currentMonth),
            Map.entry("currentYear", currentMonth.getYear())
        ), session);
    }

    @Get("/users/new")
    public HttpResponse<?> newEmployee() {
        return HttpResponse.seeOther(URI.create("/manager"));
    }

    @Get("/timesheets/{timesheetId}")
    @View("timesheet")
    public Map<String, Object> view(Principal principal, Long timesheetId, Session session) {
        WithTimesheet context = context(principal, timesheetId);
        return timesheetController.timesheetModel(context.actor(), context.subject(), context.timesheet(), session);
    }

    @Get("/employees/{userId}/timesheets/{year}/{month}")
    public HttpResponse<?> openEmployeeMonth(Principal principal, Long userId, int year, int month) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
        AppUser subject = userService.findById(userId).orElseThrow();
        MonthlyTimesheet timesheet = timesheetService.getOrCreate(actor, subject, year, month);
        return HttpResponse.seeOther(URI.create("/manager/timesheets/" + timesheet.getId()));
    }

    @Post("/timesheets/{timesheetId}")
    public HttpResponse<?> save(
        Principal principal,
        Long timesheetId,
        @Body Map<String, String> formValues,
        Session session
    ) {
        WithTimesheet context = context(principal, timesheetId);
        try {
            timesheetService.savePrivileged(
                context.actor(),
                context.subject(),
                context.timesheet(),
                timesheetController.formCommand(
                    context.timesheet().getYear(),
                    context.timesheet().getMonth(),
                    formValues
                )
            );
        } catch (TimesheetValidationException exception) {
            return HttpResponse.badRequest(new ModelAndView<>(
                "timesheet",
                timesheetController.timesheetModel(
                    context.actor(),
                    context.subject(),
                    context.timesheet(),
                    session,
                    exception.command(),
                    exception.fieldErrors()
                )
            ));
        }
        return redirect(timesheetId);
    }

    @Post("/timesheets/{timesheetId}/submit")
    public HttpResponse<?> submit(Principal principal, Long timesheetId, @Body Map<String, String> formValues) {
        WithTimesheet context = context(principal, timesheetId);
        timesheetService.submit(
            context.actor(),
            context.subject(),
            context.timesheet(),
            timesheetController.expectedVersion(formValues)
        );
        return redirect(timesheetId);
    }

    @Post("/timesheets/{timesheetId}/approve")
    public HttpResponse<?> approve(Principal principal, Long timesheetId, @Body Map<String, String> formValues) {
        WithTimesheet context = context(principal, timesheetId);
        timesheetService.approve(
            context.actor(),
            context.subject(),
            context.timesheet(),
            timesheetController.expectedVersion(formValues)
        );
        return redirect(timesheetId);
    }

    @Post("/timesheets/{timesheetId}/reopen")
    public HttpResponse<?> reopen(Principal principal, Long timesheetId, @Body Map<String, String> form) {
        WithTimesheet context = context(principal, timesheetId);
        timesheetService.reopen(
            context.actor(),
            context.subject(),
            context.timesheet(),
            form.get("reason"),
            timesheetController.expectedVersion(form)
        );
        return redirect(timesheetId);
    }

    private WithTimesheet context(Principal principal, Long timesheetId) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
        MonthlyTimesheet timesheet = timesheetRepository.findById(timesheetId).orElseThrow();
        AppUser subject = userService.findById(timesheet.getUserId()).orElseThrow();
        authorisationService.requireTimesheetScope(actor, subject);
        return new WithTimesheet(actor, subject, timesheet);
    }

    private HttpResponse<?> redirect(Long timesheetId) {
        return HttpResponse.seeOther(URI.create("/manager/timesheets/" + timesheetId));
    }

    private record WithTimesheet(AppUser actor, AppUser subject, MonthlyTimesheet timesheet) {
    }

    private String submittedBy(MonthlyTimesheet timesheet) {
        if (timesheet.getSubmittedByUserId() == null) {
            return "";
        }

        return userService.findById(timesheet.getSubmittedByUserId())
            .map(AppUser::getDisplayName)
            .orElse("Unknown user");
    }

    private Map<Long, Integer> totalsByTimesheetId(
        List<MonthlyTimesheet> awaitingApproval,
        List<MonthlyTimesheet> managedTimesheets
    ) {
        List<Long> timesheetIds = java.util.stream.Stream.concat(awaitingApproval.stream(), managedTimesheets.stream())
            .map(MonthlyTimesheet::getId)
            .distinct()
            .toList();
        if (timesheetIds.isEmpty()) {
            return Map.of();
        }

        return entryRepository.findByTimesheetIdIn(timesheetIds).stream()
            .collect(Collectors.groupingBy(
                DailyTimeEntry::getTimesheetId,
                Collectors.summingInt(DailyTimeEntry::getDurationMinutes)
            ));
    }

    private record ReviewTimesheet(
        MonthlyTimesheet timesheet,
        AppUser employee,
        String submittedBy,
        String submittedAt,
        String total
    ) {
    }

    private record ManagedTimesheet(
        MonthlyTimesheet timesheet,
        AppUser employee,
        String total
    ) {
    }
}
