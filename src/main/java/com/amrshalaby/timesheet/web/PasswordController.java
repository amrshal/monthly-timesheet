package com.amrshalaby.timesheet.web;

import com.amrshalaby.timesheet.auth.CurrentUserService;
import com.amrshalaby.timesheet.user.ChangePasswordCommand;
import com.amrshalaby.timesheet.user.UserService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.session.Session;
import io.micronaut.views.View;
import java.net.URI;
import java.security.Principal;
import java.util.Map;

@Controller
@Secured(SecurityRule.IS_AUTHENTICATED)
public class PasswordController {
    private final CurrentUserService currentUserService;
    private final UserService userService;

    public PasswordController(CurrentUserService currentUserService, UserService userService) {
        this.currentUserService = currentUserService;
        this.userService = userService;
    }

    @Get("/change-password")
    @View("change-password")
    public Map<String, Object> form(Session session) {
        return ViewModel.withCsrf(Map.of("title", "Change password"), session);
    }

    @Post("/change-password")
    public HttpResponse<?> change(Principal principal, @Body Map<String, String> form) {
        userService.changeOwnPassword(
            currentUserService.requireCurrentUser(principal),
            new ChangePasswordCommand(
                form.get("currentPassword"),
                form.get("newPassword"),
                form.get("confirmPassword")
            )
        );
        return HttpResponse.seeOther(URI.create("/timesheets"));
    }
}
