package com.amrshalaby.timesheet.admin;

import com.amrshalaby.timesheet.auth.CurrentUserService;
import com.amrshalaby.timesheet.timesheet.MonthlyTimesheet;
import com.amrshalaby.timesheet.timesheet.MonthlyTimesheetRepository;
import com.amrshalaby.timesheet.timesheet.TimesheetController;
import com.amrshalaby.timesheet.timesheet.TimesheetService;
import com.amrshalaby.timesheet.user.AppUser;
import com.amrshalaby.timesheet.user.AuthorisationService;
import com.amrshalaby.timesheet.user.UserService;
import com.amrshalaby.timesheet.web.ViewModel;
import io.micronaut.http.HttpResponse;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.session.Session;
import io.micronaut.views.View;
import java.net.URI;
import java.security.Principal;
import java.time.YearMonth;
import java.util.Map;

@Controller("/admin/timesheets")
@Secured("ADMIN")
public class AdminTimesheetController {
    private final CurrentUserService currentUserService;
    private final AuthorisationService authorisationService;
    private final MonthlyTimesheetRepository timesheetRepository;
    private final TimesheetService timesheetService;
    private final TimesheetController timesheetController;
    private final UserService userService;

    public AdminTimesheetController(
        CurrentUserService currentUserService,
        AuthorisationService authorisationService,
        MonthlyTimesheetRepository timesheetRepository,
        TimesheetService timesheetService,
        TimesheetController timesheetController,
        UserService userService
    ) {
        this.currentUserService = currentUserService;
        this.authorisationService = authorisationService;
        this.timesheetRepository = timesheetRepository;
        this.timesheetService = timesheetService;
        this.timesheetController = timesheetController;
        this.userService = userService;
    }

    @Get
    @View("dashboard")
    public Map<String, Object> search(
        Principal principal,
        @Nullable Long userId,
        @Nullable Integer year,
        @Nullable Integer month,
        Session session
    ) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
        authorisationService.requireAdministrator(actor);
        if (userId != null && year != null && month != null) {
            AppUser subject = userService.findById(userId).orElseThrow();
            MonthlyTimesheet timesheet = timesheetService.getOrCreate(actor, subject, year, month);
            return ViewModel.withCsrf(Map.of(
                "title", "Timesheet administration",
                "message", "Timesheet ready.",
                "timesheet", timesheet
            ), session);
        }
        YearMonth now = YearMonth.now();
        return ViewModel.withCsrf(Map.of(
            "title", "Timesheet administration",
            "message", "Select an employee and month to open or create a timesheet.",
            "users", userService.findAll(),
            "currentYear", now.getYear(),
            "currentMonth", now.getMonthValue()
        ), session);
    }

    @Get("/{timesheetId}")
    @View("timesheet")
    public Map<String, Object> view(Principal principal, Long timesheetId, Session session) {
        WithTimesheet context = context(principal, timesheetId);
        return timesheetController.timesheetModel(context.actor(), context.subject(), context.timesheet(), session);
    }

    @Post("/{timesheetId}")
    public HttpResponse<?> save(Principal principal, Long timesheetId, @Body Map<String, String> formValues) {
        WithTimesheet context = context(principal, timesheetId);
        timesheetService.savePrivileged(
            context.actor(),
            context.subject(),
            context.timesheet(),
            timesheetController.formCommand(context.timesheet().getYear(), context.timesheet().getMonth(), formValues)
        );
        return redirect(timesheetId);
    }

    @Post("/{timesheetId}/submit")
    public HttpResponse<?> submit(Principal principal, Long timesheetId) {
        WithTimesheet context = context(principal, timesheetId);
        timesheetService.submit(context.actor(), context.subject(), context.timesheet());
        return redirect(timesheetId);
    }

    @Post("/{timesheetId}/approve")
    public HttpResponse<?> approve(Principal principal, Long timesheetId) {
        WithTimesheet context = context(principal, timesheetId);
        timesheetService.approve(context.actor(), context.subject(), context.timesheet());
        return redirect(timesheetId);
    }

    @Post("/{timesheetId}/reopen")
    public HttpResponse<?> reopen(Principal principal, Long timesheetId, @Body Map<String, String> form) {
        WithTimesheet context = context(principal, timesheetId);
        timesheetService.reopen(context.actor(), context.subject(), context.timesheet(), form.get("reason"));
        return redirect(timesheetId);
    }

    private WithTimesheet context(Principal principal, Long timesheetId) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
        authorisationService.requireAdministrator(actor);
        MonthlyTimesheet timesheet = timesheetRepository.findById(timesheetId).orElseThrow();
        AppUser subject = userService.findById(timesheet.getUserId()).orElseThrow();
        return new WithTimesheet(actor, subject, timesheet);
    }

    private HttpResponse<?> redirect(Long timesheetId) {
        return HttpResponse.seeOther(URI.create("/admin/timesheets/" + timesheetId));
    }

    private record WithTimesheet(AppUser actor, AppUser subject, MonthlyTimesheet timesheet) {
    }
}
