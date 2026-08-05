package com.amrshalaby.timesheet.auth;

import com.amrshalaby.timesheet.user.AppUser;
import com.amrshalaby.timesheet.user.UserService;
import jakarta.inject.Singleton;
import java.security.Principal;

@Singleton
public class CurrentUserService {
    private final UserService userService;

    public CurrentUserService(UserService userService) {
        this.userService = userService;
    }

    public AppUser requireCurrentUser(Principal principal) {
        if (principal == null) {
            throw new SecurityException("Authentication is required.");
        }

        return userService.findActiveByEmail(principal.getName())
            .orElseThrow(() -> new SecurityException("Authenticated user was not found."));
    }
}
