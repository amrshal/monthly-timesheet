package com.amrshalaby.timesheet.user;

public record ChangePasswordCommand(
    String currentPassword,
    String newPassword,
    String confirmPassword
) {
}
