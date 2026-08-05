package com.amrshalaby.timesheet.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthorisationServiceTest {
    private final AuthorisationService service = new AuthorisationService(false);

    @Test
    void administratorCanManageEveryone() {
        AppUser admin = user(1L, UserRole.ADMIN, null);
        AppUser employee = user(2L, UserRole.EMPLOYEE, null);

        assertTrue(service.canManageUser(admin, employee));
    }

    @Test
    void managerCanManageAssignedEmployeeOnly() {
        AppUser manager = user(1L, UserRole.MANAGER, null);
        AppUser assigned = user(2L, UserRole.EMPLOYEE, 1L);
        AppUser unassigned = user(3L, UserRole.EMPLOYEE, 9L);

        assertTrue(service.canManageUser(manager, assigned));
        assertFalse(service.canManageUser(manager, unassigned));
    }

    @Test
    void inactiveActorsCannotManageUsers() {
        AppUser admin = user(1L, UserRole.ADMIN, null);
        admin.setActive(false);
        AppUser employee = user(2L, UserRole.EMPLOYEE, null);

        assertFalse(service.canManageUser(admin, employee));
    }

    @Test
    void managerCannotCreateEmployeesByDefaultAndCannotAdministerUsers() {
        AppUser manager = user(1L, UserRole.MANAGER, null);

        assertFalse(service.canManagerCreateEmployee(manager));
        assertFalse(service.canAdministerUsers(manager));
    }

    @Test
    void employeeCanManageOnlySelf() {
        AppUser employee = user(1L, UserRole.EMPLOYEE, null);
        AppUser other = user(2L, UserRole.EMPLOYEE, null);

        assertTrue(service.canManageUser(employee, employee));
        assertFalse(service.canManageUser(employee, other));
    }

    private AppUser user(Long id, UserRole role, Long managerId) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        user.setManagerId(managerId);
        user.setActive(true);
        return user;
    }
}
