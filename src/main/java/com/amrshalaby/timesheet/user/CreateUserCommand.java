package com.amrshalaby.timesheet.user;

public record CreateUserCommand(
    String email,
    String displayName,
    String temporaryPassword,
    UserRole role,
    Long managerId,
    boolean mustChangePassword
) {
}
