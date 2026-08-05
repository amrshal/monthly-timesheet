package com.amrshalaby.timesheet.manager;

import com.amrshalaby.timesheet.auth.CurrentUserService;
import com.amrshalaby.timesheet.timesheet.MonthlyTimesheet;
import com.amrshalaby.timesheet.timesheet.MonthlyTimesheetRepository;
import com.amrshalaby.timesheet.timesheet.TimesheetController;
import com.amrshalaby.timesheet.timesheet.TimesheetService;
import com.amrshalaby.timesheet.timesheet.TimesheetStatus;
import com.amrshalaby.timesheet.user.AppUser;
import com.amrshalaby.timesheet.user.AuthorisationService;
import com.amrshalaby.timesheet.user.CreateUserCommand;
import com.amrshalaby.timesheet.user.UserRepository;
import com.amrshalaby.timesheet.user.UserRole;
import com.amrshalaby.timesheet.user.UserService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.views.View;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

@Controller("/manager")
@Secured({"MANAGER", "ADMIN"})
public class ManagerController {
    private final CurrentUserService currentUserService;
    private final AuthorisationService authorisationService;
    private final MonthlyTimesheetRepository timesheetRepository;
    private final TimesheetService timesheetService;
    private final TimesheetController timesheetController;
    private final UserRepository userRepository;
    private final UserService userService;

    public ManagerController(
        CurrentUserService currentUserService,
        AuthorisationService authorisationService,
        MonthlyTimesheetRepository timesheetRepository,
        TimesheetService timesheetService,
        TimesheetController timesheetController,
        UserRepository userRepository,
        UserService userService
    ) {
        this.currentUserService = currentUserService;
        this.authorisationService = authorisationService;
        this.timesheetRepository = timesheetRepository;
        this.timesheetService = timesheetService;
        this.timesheetController = timesheetController;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Get
    @View("manager-dashboard")
    public Map<String, Object> dashboard(Principal principal) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
        List<AppUser> employees = userRepository.findByManagerId(actor.getId());
        Set<Long> employeeIds = employees.stream().map(AppUser::getId).collect(java.util.stream.Collectors.toSet());
        List<MonthlyTimesheet> awaitingApproval = StreamSupport.stream(
                timesheetRepository.findByStatus(TimesheetStatus.SUBMITTED).spliterator(),
                false
            )
            .filter(timesheet -> employeeIds.contains(timesheet.getUserId()))
            .toList();

        return Map.of(
            "title", "Manager dashboard",
            "employees", employees,
            "awaitingApproval", awaitingApproval
        );
    }

    @Get("/users/new")
    @View("manager-user-form")
    public Map<String, Object> newEmployee() {
        return Map.of("title", "Create employee");
    }

    @Post("/users")
    public HttpResponse<?> createEmployee(Principal principal, @Body Map<String, String> form) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
        userService.createUser(
            actor,
            new CreateUserCommand(
                form.get("email"),
                form.get("displayName"),
                form.get("temporaryPassword"),
                UserRole.EMPLOYEE,
                actor.getId(),
                true
            )
        );
        return HttpResponse.seeOther(URI.create("/manager"));
    }

    @Get("/timesheets/{timesheetId}")
    @View("timesheet")
    public Map<String, Object> view(Principal principal, Long timesheetId) {
        WithTimesheet context = context(principal, timesheetId);
        return timesheetController.timesheetModel(context.actor(), context.subject(), context.timesheet());
    }

    @Post("/timesheets/{timesheetId}")
    public HttpResponse<?> save(
        Principal principal,
        Long timesheetId,
        @Body Map<String, String> formValues
    ) {
        WithTimesheet context = context(principal, timesheetId);
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
        return redirect(timesheetId);
    }

    @Post("/timesheets/{timesheetId}/submit")
    public HttpResponse<?> submit(Principal principal, Long timesheetId) {
        WithTimesheet context = context(principal, timesheetId);
        timesheetService.submit(context.actor(), context.subject(), context.timesheet());
        return redirect(timesheetId);
    }

    @Post("/timesheets/{timesheetId}/approve")
    public HttpResponse<?> approve(Principal principal, Long timesheetId) {
        WithTimesheet context = context(principal, timesheetId);
        timesheetService.approve(context.actor(), context.subject(), context.timesheet());
        return redirect(timesheetId);
    }

    @Post("/timesheets/{timesheetId}/reopen")
    public HttpResponse<?> reopen(Principal principal, Long timesheetId, @QueryValue String reason) {
        WithTimesheet context = context(principal, timesheetId);
        timesheetService.reopen(context.actor(), context.subject(), context.timesheet(), reason);
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
}
