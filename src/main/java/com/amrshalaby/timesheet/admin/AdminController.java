package com.amrshalaby.timesheet.admin;

import com.amrshalaby.timesheet.audit.AuditEvent;
import com.amrshalaby.timesheet.audit.AuditEventRepository;
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
import io.micronaut.views.View;
import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Controller("/admin")
@Secured("ADMIN")
public class AdminController {
    private final AuditEventRepository auditEventRepository;
    private final CurrentUserService currentUserService;
    private final UserService userService;
    private final UserRepository userRepository;

    public AdminController(
        AuditEventRepository auditEventRepository,
        CurrentUserService currentUserService,
        UserService userService,
        UserRepository userRepository
    ) {
        this.auditEventRepository = auditEventRepository;
        this.currentUserService = currentUserService;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Get("/users")
    @View("admin-users")
    public Map<String, Object> users(Session session) {
        return ViewModel.withCsrf(Map.of("title", "User administration", "users", userRepository.findAll()), session);
    }

    @Get("/users/new")
    @View("admin-user-form")
    public Map<String, Object> newUser(Session session) {
        return ViewModel.withCsrf(Map.of("title", "Create user", "roles", UserRole.values()), session);
    }

    @Post("/users")
    public HttpResponse<?> createUser(Principal principal, @Body Map<String, String> form) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
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
        return HttpResponse.seeOther(URI.create("/admin/users"));
    }

    @Get("/users/{userId}")
    @View("admin-user-form")
    public Map<String, Object> editUser(Long userId, Session session) {
        return ViewModel.withCsrf(Map.of(
            "title", "Edit user",
            "user", userRepository.findById(userId).orElseThrow(),
            "roles", UserRole.values()
        ), session);
    }

    @Post("/users/{userId}")
    public HttpResponse<?> updateUser(Principal principal, Long userId, @Body Map<String, String> form) {
        AppUser actor = currentUserService.requireCurrentUser(principal);
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
        @Body Map<String, String> form
    ) {
        userService.resetPassword(currentUserService.requireCurrentUser(principal), userId, form.get("temporaryPassword"));
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
            Map.entry("events", events),
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
}
