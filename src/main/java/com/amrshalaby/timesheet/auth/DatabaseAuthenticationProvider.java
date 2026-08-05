package com.amrshalaby.timesheet.auth;

import com.amrshalaby.timesheet.audit.AuditEventType;
import com.amrshalaby.timesheet.audit.AuditService;
import com.amrshalaby.timesheet.user.AppUser;
import com.amrshalaby.timesheet.user.UserService;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider;
import jakarta.inject.Singleton;
import java.util.List;
import org.reactivestreams.Publisher;

@Singleton
public class DatabaseAuthenticationProvider implements HttpRequestAuthenticationProvider<Object> {
    private final UserService userService;
    private final PasswordHasher passwordHasher;
    private final AuditService auditService;

    public DatabaseAuthenticationProvider(
        UserService userService,
        PasswordHasher passwordHasher,
        AuditService auditService
    ) {
        this.userService = userService;
        this.passwordHasher = passwordHasher;
        this.auditService = auditService;
    }

    @Override
    public Publisher<AuthenticationResponse> authenticate(
        HttpRequest<Object> requestContext,
        AuthenticationRequest<String, String> authenticationRequest
    ) {
        String email = authenticationRequest.getIdentity();
        String password = authenticationRequest.getSecret();
        return userService.findActiveByEmail(email)
            .filter(user -> passwordHasher.matches(password, user.getPasswordHash()))
            .map(user -> success(user, requestContext))
            .orElseGet(() -> failure(email, requestContext));
    }

    private Publisher<AuthenticationResponse> success(AppUser user, HttpRequest<Object> request) {
        auditService.record(
            user.getId(),
            user.getId(),
            AuditEventType.LOGIN_SUCCEEDED.name(),
            "user",
            user.getId(),
            requestDetails(request)
        );
        return Publishers.just(AuthenticationResponse.success(user.getEmail(), List.of(user.getRole().name())));
    }

    private Publisher<AuthenticationResponse> failure(String email, HttpRequest<Object> request) {
        auditService.record(
            null,
            null,
            AuditEventType.LOGIN_FAILED.name(),
            "user",
            null,
            requestDetails(request)
        );
        return Publishers.just(AuthenticationResponse.failure("Invalid email or password."));
    }

    private String requestDetails(HttpRequest<Object> request) {
        return "{\"ip_address\":\"" + request.getRemoteAddress().getAddress().getHostAddress() + "\"}";
    }
}
