package com.amrshalaby.timesheet.user;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
public class AuthorisationService {
    private final boolean managerUserCreationEnabled;

    public AuthorisationService(@Value("${app.manager-user-creation.enabled:false}") boolean managerUserCreationEnabled) {
        this.managerUserCreationEnabled = managerUserCreationEnabled;
    }

    public boolean canManageUser(AppUser actor, AppUser subject) {
        if (actor == null || subject == null || !actor.isActive()) {
            return false;
        }
        if (actor.getRole() == UserRole.ADMIN) {
            return true;
        }
        if (actor.getRole() == UserRole.MANAGER) {
            return subject.getManagerId() != null && subject.getManagerId().equals(actor.getId());
        }

        return actor.getId() != null && actor.getId().equals(subject.getId());
    }

    public boolean canAdministerUsers(AppUser actor) {
        return actor != null && actor.isActive() && actor.getRole() == UserRole.ADMIN;
    }

    public boolean canManagerCreateEmployee(AppUser actor) {
        return managerUserCreationEnabled && actor != null && actor.isActive() && actor.getRole() == UserRole.MANAGER;
    }

    public void requireTimesheetScope(AppUser actor, AppUser subject) {
        if (!canManageUser(actor, subject)) {
            throw new SecurityException("You are not authorised to access this employee.");
        }
    }

    public void requireAdministrator(AppUser actor) {
        if (!canAdministerUsers(actor)) {
            throw new SecurityException("Administrator access is required.");
        }
    }
}
