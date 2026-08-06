package com.amrshalaby.timesheet.admin;

import com.amrshalaby.timesheet.audit.AuditEvent;
import com.amrshalaby.timesheet.audit.AuditEventRepository;
import com.amrshalaby.timesheet.audit.AuditViewService;
import com.amrshalaby.timesheet.auth.CurrentUserService;
import com.amrshalaby.timesheet.user.AppUser;
import com.amrshalaby.timesheet.user.CreateUserCommand;
import com.amrshalaby.timesheet.user.UpdateUserCommand;
import com.amrshalaby.timesheet.user.UserRepository;
import com.amrshalaby.timesheet.user.UserRole;
import com.amrshalaby.timesheet.user.UserService;
import com.amrshalaby.timesheet.web.ViewModel;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.annotation.Secured;
import io.micronaut.session.Session;
import io.micronaut.views.ModelAndView;
import io.micronaut.views.View;
import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

@Controller("/admin")
@Secured("ADMIN")
public class AdminController {
    private final AuditEventRepository auditEventRepository;
    private final CurrentUserService currentUserService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final AuditViewService auditViewService;

    public AdminController(
        AuditEventRepository auditEventRepository,
        CurrentUserService currentUserService,
        UserService userService,
        UserRepository userRepository,
        AuditViewService auditViewService
    ) {
        this.auditEventRepository = auditEventRepository;
        this.currentUserService = currentUserService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.auditViewService = auditViewService;
    }

    @Get("/users")
    @View("admin-users")
    public Map<String, Object> users(Session session) {
        return ViewModel.withCsrf(Map.of("title", "User administration", "users", userRepository.findAll()), session);
    }

    @Get("/users/new")
    @View("admin-user-form")
    public Map<String, Object> newUser(Session session) {
        return ViewModel.withCsrf(userFormModel("Create user", null, Map.of(), Map.of()), session);
    }

    @Post("/users")
    public HttpResponse<?> createUser(Principal principal, @Body Map<String, String> form, Session session) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
        try {
            userService.createUser(
                actor,
                new CreateUserCommand(
                    form.get("email"),
                    form.get("displayName"),
                    form.get("temporaryPassword"),
                    UserRole.valueOf(form.get("role")),
                    parseLong(form.get("managerId")),
                    "true".equals(form.get("mustChangePassword"))
                )
            );
        } catch (IllegalArgumentException exception) {
            return HttpResponse.badRequest(new ModelAndView<>(
                "admin-user-form",
                ViewModel.withCsrf(userFormModel("Create user", null, form, fieldErrors(exception)), session)
            ));
        }
        return HttpResponse.seeOther(URI.create("/admin/users"));
    }

    @Get("/users/{userId}")
    @View("admin-user-form")
    public Map<String, Object> editUser(Long userId, Session session) {
        return ViewModel.withCsrf(
            userFormModel("Edit user", userRepository.findById(userId).orElseThrow(), Map.of(), Map.of()),
            session
        );
    }

    @Post("/users/{userId}")
    public HttpResponse<?> updateUser(Principal principal, Long userId, @Body Map<String, String> form, Session session) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
        try {
            userService.updateUser(
                actor,
                userId,
                new UpdateUserCommand(
                    form.get("email"),
                    form.get("displayName"),
                    UserRole.valueOf(form.get("role")),
                    parseLong(form.get("managerId")),
                    "true".equals(form.get("active")),
                    "true".equals(form.get("mustChangePassword"))
                )
            );
        } catch (IllegalArgumentException exception) {
            return HttpResponse.badRequest(new ModelAndView<>(
                "admin-user-form",
                ViewModel.withCsrf(
                    userFormModel("Edit user", userRepository.findById(userId).orElseThrow(), form, fieldErrors(exception)),
                    session
                )
            ));
        }
        return HttpResponse.seeOther(URI.create("/admin/users"));
    }

    @Post("/users/{userId}/disable")
    public HttpResponse<?> disable(Principal principal, Long userId) {
        userService.disable(currentUserService.requireCurrentUser(principal), userId);
        return HttpResponse.seeOther(URI.create("/admin/users"));
    }

    @Post("/users/{userId}/reactivate")
    public HttpResponse<?> reactivate(Principal principal, Long userId) {
        userService.reactivate(currentUserService.requireCurrentUser(principal), userId);
        return HttpResponse.seeOther(URI.create("/admin/users"));
    }

    @Post("/users/{userId}/reset-password")
    public HttpResponse<?> resetPassword(
        Principal principal,
        Long userId,
        @Body Map<String, String> form,
        Session session
    ) {
        try {
            userService.resetPassword(currentUserService.requireCurrentUser(principal), userId, form.get("temporaryPassword"));
        } catch (IllegalArgumentException exception) {
            return HttpResponse.badRequest(new ModelAndView<>(
                "admin-user-form",
                ViewModel.withCsrf(
                    userFormModel(
                        "Edit user",
                        userRepository.findById(userId).orElseThrow(),
                        Map.of(),
                        Map.of("temporaryPassword", exception.getMessage())
                    ),
                    session
                )
            ));
        }
        return HttpResponse.seeOther(URI.create("/admin/users/" + userId));
    }

    @Get("/audit")
    @View("audit-log")
    public Map<String, Object> audit(
        @Nullable Long actorUserId,
        @Nullable Long subjectUserId,
        @Nullable String eventType,
        @Nullable Long timesheetId,
        @Nullable LocalDate fromDate,
        @Nullable LocalDate toDate,
        Session session
    ) {
        List<AuditEvent> events = StreamSupport.stream(auditEventRepository.findAll().spliterator(), false)
            .filter(event -> actorUserId == null || actorUserId.equals(event.getActorUserId()))
            .filter(event -> subjectUserId == null || subjectUserId.equals(event.getSubjectUserId()))
            .filter(event -> eventType == null || eventType.isBlank() || eventType.equals(event.getEventType()))
            .filter(event -> timesheetId == null
                || ("monthly_timesheet".equals(event.getEntityType()) && timesheetId.equals(event.getEntityId())))
            .filter(event -> fromDate == null
                || !event.getEventTime().isBefore(fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)))
            .filter(event -> toDate == null
                || event.getEventTime().isBefore(toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)))
            .toList();
        return ViewModel.withCsrf(Map.ofEntries(
            Map.entry("title", "Audit log"),
            Map.entry("events", auditViewService.toViews(events)),
            Map.entry("users", userService.findAll()),
            Map.entry("eventTypes", com.amrshalaby.timesheet.audit.AuditEventType.values()),
            Map.entry("actorUserId", actorUserId == null ? "" : actorUserId),
            Map.entry("subjectUserId", subjectUserId == null ? "" : subjectUserId),
            Map.entry("eventType", eventType == null ? "" : eventType),
            Map.entry("timesheetId", timesheetId == null ? "" : timesheetId),
            Map.entry("fromDate", fromDate == null ? "" : fromDate),
            Map.entry("toDate", toDate == null ? "" : toDate)
        ), session);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Long.valueOf(value);
    }

    private List<AppUser> managers() {
        return userService.findAll().stream()
            .filter(user -> user.getRole() == UserRole.MANAGER && user.isActive())
            .toList();
    }

    private Map<String, Object> userFormModel(
        String title,
        AppUser user,
        Map<String, String> form,
        Map<String, String> fieldErrors
    ) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("title", title);
        if (user != null) {
            model.put("user", user);
        }
        model.put("form", form);
        model.put("fieldErrors", fieldErrors);
        model.put("roles", UserRole.values());
        model.put("managers", managers());
        return model;
    }

    private Map<String, String> fieldErrors(IllegalArgumentException exception) {
        Map<String, String> errors = new HashMap<>();
        String message = exception.getMessage();
        if (message == null) {
            errors.put("form", "Check your input.");
        } else if (message.contains("Display name")) {
            errors.put("displayName", message);
        } else if (message.contains("Email") || message.contains("email")) {
            errors.put("email", message);
        } else if (message.contains("Password") || message.contains("password")) {
            errors.put("temporaryPassword", message);
        } else if (message.contains("manager")) {
            errors.put("managerId", message);
        } else if (message.contains("Role")) {
            errors.put("role", message);
        } else {
            errors.put("form", message);
        }
        return errors;
    }
}
