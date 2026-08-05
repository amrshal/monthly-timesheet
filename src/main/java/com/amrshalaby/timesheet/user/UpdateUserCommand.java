package com.amrshalaby.timesheet.user;

public record UpdateUserCommand(
    String email,
    String displayName,
    UserRole role,
    Long managerId,
    boolean active,
    boolean mustChangePassword
) {
}
