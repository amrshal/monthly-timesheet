package com.amrshalaby.timesheet.user;

import com.amrshalaby.timesheet.audit.AuditEventType;
import com.amrshalaby.timesheet.audit.AuditService;
import com.amrshalaby.timesheet.auth.PasswordHasher;
import com.amrshalaby.timesheet.common.EmailNormalizer;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Singleton
public class UserService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AuthorisationService authorisationService;
    private final AuditService auditService;
    private final int minimumPasswordLength;

    public UserService(
        UserRepository userRepository,
        PasswordHasher passwordHasher,
        AuthorisationService authorisationService,
        AuditService auditService,
        @Value("${app.password.minimum-length:12}") int minimumPasswordLength
    ) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.authorisationService = authorisationService;
        this.auditService = auditService;
        this.minimumPasswordLength = minimumPasswordLength;
    }

    public Optional<AppUser> findActiveByEmail(String email) {
        return userRepository.findByEmail(EmailNormalizer.normalize(email))
            .filter(AppUser::isActive);
    }

    public Optional<AppUser> findById(Long id) {
        return userRepository.findById(id);
    }

    public List<AppUser> findAll() {
        return StreamSupport.stream(userRepository.findAll().spliterator(), false).toList();
    }

    @Transactional
    public AppUser createUser(AppUser actor, CreateUserCommand command) {
        validateCreateActor(actor, command);
        validateUser(command.email(), command.displayName(), command.role(), command.managerId());
        validatePassword(command.temporaryPassword());
        ensureEmailAvailable(command.email(), null);

        AppUser user = new AppUser();
        user.setEmail(EmailNormalizer.normalize(command.email()));
        user.setDisplayName(command.displayName().trim());
        user.setPasswordHash(passwordHasher.hash(command.temporaryPassword()));
        user.setRole(command.role());
        user.setManagerId(managerIdForCreate(actor, command));
        user.setActive(true);
        user.setMustChangePassword(command.mustChangePassword());

        AppUser saved = userRepository.save(user);
        auditService.record(
            actor == null ? null : actor.getId(),
            saved.getId(),
            AuditEventType.USER_CREATED.name(),
            "user",
            saved.getId(),
            userDetails(null, saved)
        );
        return saved;
    }

    @Transactional
    public AppUser updateUser(AppUser actor, Long userId, UpdateUserCommand command) {
        authorisationService.requireAdministrator(actor);
        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));
        validateUser(command.email(), command.displayName(), command.role(), command.managerId());
        ensureEmailAvailable(command.email(), userId);
        if (command.managerId() != null && command.managerId().equals(userId)) {
            throw new IllegalArgumentException("A user cannot manage themselves.");
        }

        UserRole previousRole = user.getRole();
        Long previousManager = user.getManagerId();
        String previousEmail = user.getEmail();
        String previousDisplayName = user.getDisplayName();
        boolean previousActive = user.isActive();
        boolean previousMustChangePassword = user.isMustChangePassword();
        user.setEmail(EmailNormalizer.normalize(command.email()));
        user.setDisplayName(command.displayName().trim());
        user.setRole(command.role());
        user.setManagerId(command.managerId());
        user.setActive(command.active());
        user.setMustChangePassword(command.mustChangePassword());

        AppUser saved = userRepository.update(user);
        auditService.record(
            actor.getId(),
            saved.getId(),
            AuditEventType.USER_UPDATED.name(),
            "user",
            saved.getId(),
            userUpdateDetails(
                previousEmail,
                saved.getEmail(),
                previousDisplayName,
                saved.getDisplayName(),
                previousRole,
                saved.getRole(),
                previousManager,
                saved.getManagerId(),
                previousActive,
                saved.isActive(),
                previousMustChangePassword,
                saved.isMustChangePassword()
            )
        );
        if (previousRole != saved.getRole()) {
            auditService.record(
                actor.getId(),
                saved.getId(),
                AuditEventType.USER_ROLE_CHANGED.name(),
                "user",
                saved.getId(),
                simpleChangeDetails("role", previousRole.name(), saved.getRole().name())
            );
        }
        if (!java.util.Objects.equals(previousManager, saved.getManagerId())) {
            auditService.record(
                actor.getId(),
                saved.getId(),
                AuditEventType.USER_MANAGER_CHANGED.name(),
                "user",
                saved.getId(),
                simpleChangeDetails("manager_id", stringValue(previousManager), stringValue(saved.getManagerId()))
            );
        }

        return saved;
    }


    @Transactional
    public void changeOwnPassword(AppUser actor, ChangePasswordCommand command) {
        if (actor == null || !actor.isActive()) {
            throw new SecurityException("Authentication is required.");
        }
        if (!passwordHasher.matches(command.currentPassword(), actor.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (command.newPassword() == null || !command.newPassword().equals(command.confirmPassword())) {
            throw new IllegalArgumentException("New password and confirmation must match.");
        }
        validatePassword(command.newPassword());

        actor.setPasswordHash(passwordHasher.hash(command.newPassword()));
        actor.setMustChangePassword(false);
        userRepository.update(actor);
        auditService.record(
            actor.getId(),
            actor.getId(),
            AuditEventType.USER_UPDATED.name(),
            "user",
            actor.getId(),
            "{\"password_changed\":\"true\"}"
        );
    }

    @Transactional
    public void resetPassword(AppUser actor, Long userId, String temporaryPassword) {
        authorisationService.requireAdministrator(actor);
        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));
        validatePassword(temporaryPassword);
        user.setPasswordHash(passwordHasher.hash(temporaryPassword));
        user.setMustChangePassword(true);
        userRepository.update(user);
        auditService.record(
            actor.getId(),
            user.getId(),
            AuditEventType.PASSWORD_RESET.name(),
            "user",
            user.getId(),
            "{\"must_change_password_after\":\"true\"}"
        );
    }

    @Transactional
    public void disable(AppUser actor, Long userId) {
        setActive(actor, userId, false, AuditEventType.USER_DISABLED);
    }

    @Transactional
    public void reactivate(AppUser actor, Long userId) {
        setActive(actor, userId, true, AuditEventType.USER_REACTIVATED);
    }

    private void setActive(AppUser actor, Long userId, boolean active, AuditEventType eventType) {
        authorisationService.requireAdministrator(actor);
        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));
        boolean previousActive = user.isActive();
        user.setActive(active);
        userRepository.update(user);
        auditService.record(
            actor.getId(),
            user.getId(),
            eventType.name(),
            "user",
            user.getId(),
            simpleChangeDetails("active", String.valueOf(previousActive), String.valueOf(active))
        );
    }

    private void validateCreateActor(AppUser actor, CreateUserCommand command) {
        if (actor == null && command.role() == UserRole.ADMIN) {
            return;
        }
        if (authorisationService.canAdministerUsers(actor)) {
            return;
        }
        if (authorisationService.canManagerCreateEmployee(actor) && command.role() == UserRole.EMPLOYEE) {
            return;
        }

        throw new SecurityException("You are not authorised to create this user.");
    }

    private Long managerIdForCreate(AppUser actor, CreateUserCommand command) {
        if (actor != null && actor.getRole() == UserRole.MANAGER) {
            return actor.getId();
        }

        return command.managerId();
    }

    private void validateUser(String email, String displayName, UserRole role, Long managerId) {
        EmailNormalizer.normalize(email);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required.");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role is required.");
        }
        if (managerId != null && userRepository.findById(managerId).filter(u -> u.getRole() == UserRole.MANAGER && u.isActive()).isEmpty()) {
            throw new IllegalArgumentException("Assigned manager must be an active manager.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < minimumPasswordLength) {
            throw new IllegalArgumentException("Password must be at least " + minimumPasswordLength + " characters.");
        }
    }

    private void ensureEmailAvailable(String email, Long existingUserId) {
        userRepository.findByEmail(EmailNormalizer.normalize(email))
            .filter(existing -> existingUserId == null || !existing.getId().equals(existingUserId))
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Email address is already in use.");
            });
    }

    private String userDetails(AppUser before, AppUser after) {
        return "{"
            + jsonField("email_before", before == null ? null : before.getEmail()) + ","
            + jsonField("email_after", after.getEmail()) + ","
            + jsonField("display_name_before", before == null ? null : before.getDisplayName()) + ","
            + jsonField("display_name_after", after.getDisplayName()) + ","
            + jsonField("role_before", before == null ? null : before.getRole().name()) + ","
            + jsonField("role_after", after.getRole().name()) + ","
            + jsonField("manager_id_before", before == null ? null : stringValue(before.getManagerId())) + ","
            + jsonField("manager_id_after", stringValue(after.getManagerId())) + ","
            + jsonField("active_before", before == null ? null : String.valueOf(before.isActive())) + ","
            + jsonField("active_after", String.valueOf(after.isActive())) + ","
            + jsonField("must_change_password_before", before == null ? null : String.valueOf(before.isMustChangePassword())) + ","
            + jsonField("must_change_password_after", String.valueOf(after.isMustChangePassword()))
            + "}";
    }

    private String userUpdateDetails(
        String emailBefore,
        String emailAfter,
        String displayNameBefore,
        String displayNameAfter,
        UserRole roleBefore,
        UserRole roleAfter,
        Long managerBefore,
        Long managerAfter,
        boolean activeBefore,
        boolean activeAfter,
        boolean mustChangePasswordBefore,
        boolean mustChangePasswordAfter
    ) {
        return "{"
            + jsonField("email_before", emailBefore) + ","
            + jsonField("email_after", emailAfter) + ","
            + jsonField("display_name_before", displayNameBefore) + ","
            + jsonField("display_name_after", displayNameAfter) + ","
            + jsonField("role_before", roleBefore.name()) + ","
            + jsonField("role_after", roleAfter.name()) + ","
            + jsonField("manager_id_before", stringValue(managerBefore)) + ","
            + jsonField("manager_id_after", stringValue(managerAfter)) + ","
            + jsonField("active_before", String.valueOf(activeBefore)) + ","
            + jsonField("active_after", String.valueOf(activeAfter)) + ","
            + jsonField("must_change_password_before", String.valueOf(mustChangePasswordBefore)) + ","
            + jsonField("must_change_password_after", String.valueOf(mustChangePasswordAfter))
            + "}";
    }

    private String simpleChangeDetails(String field, String before, String after) {
        return "{"
            + jsonField("field", field) + ","
            + jsonField("before", before) + ","
            + jsonField("after", after)
            + "}";
    }

    private String stringValue(Long value) {
        return value == null ? null : value.toString();
    }

    private String jsonField(String name, String value) {
        return "\"" + escapeJson(name) + "\":" + (value == null ? "null" : "\"" + escapeJson(value) + "\"");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
