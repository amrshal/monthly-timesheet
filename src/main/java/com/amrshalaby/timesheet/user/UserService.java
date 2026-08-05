package com.amrshalaby.timesheet.user;

import com.amrshalaby.timesheet.audit.AuditEventType;
import com.amrshalaby.timesheet.audit.AuditService;
import com.amrshalaby.timesheet.auth.PasswordHasher;
import com.amrshalaby.timesheet.common.EmailNormalizer;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.util.Optional;

@Singleton
public class UserService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AuthorisationService authorisationService;
    private final AuditService auditService;

    public UserService(
        UserRepository userRepository,
        PasswordHasher passwordHasher,
        AuthorisationService authorisationService,
        AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.authorisationService = authorisationService;
        this.auditService = auditService;
    }

    public Optional<AppUser> findActiveByEmail(String email) {
        return userRepository.findByEmail(EmailNormalizer.normalize(email))
            .filter(AppUser::isActive);
    }

    public Optional<AppUser> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional
    public AppUser createUser(AppUser actor, CreateUserCommand command) {
        validateCreateActor(actor, command);
        validateUser(command.email(), command.displayName(), command.role(), command.managerId());
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
            "{}"
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
        user.setEmail(EmailNormalizer.normalize(command.email()));
        user.setDisplayName(command.displayName().trim());
        user.setRole(command.role());
        user.setManagerId(command.managerId());
        user.setActive(command.active());
        user.setMustChangePassword(command.mustChangePassword());

        AppUser saved = userRepository.update(user);
        auditService.record(actor.getId(), saved.getId(), AuditEventType.USER_UPDATED.name(), "user", saved.getId(), "{}");
        if (previousRole != saved.getRole()) {
            auditService.record(actor.getId(), saved.getId(), AuditEventType.USER_ROLE_CHANGED.name(), "user", saved.getId(), "{}");
        }
        if (!java.util.Objects.equals(previousManager, saved.getManagerId())) {
            auditService.record(actor.getId(), saved.getId(), AuditEventType.USER_MANAGER_CHANGED.name(), "user", saved.getId(), "{}");
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
        user.setPasswordHash(passwordHasher.hash(temporaryPassword));
        user.setMustChangePassword(true);
        userRepository.update(user);
        auditService.record(actor.getId(), user.getId(), AuditEventType.PASSWORD_RESET.name(), "user", user.getId(), "{}");
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
        user.setActive(active);
        userRepository.update(user);
        auditService.record(actor.getId(), user.getId(), eventType.name(), "user", user.getId(), "{}");
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
        if (managerId != null && userRepository.findById(managerId).filter(u -> u.getRole() == UserRole.MANAGER).isEmpty()) {
            throw new IllegalArgumentException("Assigned manager must be an active manager.");
        }
    }

    private void ensureEmailAvailable(String email, Long existingUserId) {
        userRepository.findByEmail(EmailNormalizer.normalize(email))
            .filter(existing -> existingUserId == null || !existing.getId().equals(existingUserId))
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Email address is already in use.");
            });
    }
}
