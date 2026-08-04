package com.amrshalaby.timesheet.timesheet;

import com.amrshalaby.timesheet.user.UserRole;

public final class StatusTransitionPolicy {
    private StatusTransitionPolicy() {}
    public static boolean canSubmit(TimesheetStatus status, UserRole role, boolean ownerOrScoped) {
        return ownerOrScoped && status == TimesheetStatus.DRAFT;
    }
    public static boolean canApprove(TimesheetStatus status, UserRole role, boolean scoped) {
        return scoped && status == TimesheetStatus.SUBMITTED && (role == UserRole.MANAGER || role == UserRole.ADMIN);
    }
    public static boolean canReopen(TimesheetStatus status, UserRole role, boolean scoped) {
        if (!scoped) return false;
        return status == TimesheetStatus.SUBMITTED && (role == UserRole.MANAGER || role == UserRole.ADMIN)
            || status == TimesheetStatus.APPROVED && role == UserRole.ADMIN;
    }
    public static boolean employeeCanEdit(TimesheetStatus status, boolean owner) { return owner && status == TimesheetStatus.DRAFT; }
    public static boolean privilegedCanEdit(UserRole role, boolean scoped) { return scoped && (role == UserRole.MANAGER || role == UserRole.ADMIN); }
}
