package com.amrshalaby.timesheet.web;

import com.amrshalaby.timesheet.user.UserService;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import java.net.URI;
import java.util.Optional;
import org.reactivestreams.Publisher;

@Filter("/**")
public class MustChangePasswordFilter implements HttpServerFilter {
    private final UserService userService;

    public MustChangePasswordFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        if (isAllowedPath(request.getPath())) {
            return chain.proceed(request);
        }

        Optional<Boolean> mustChangePassword = request.getUserPrincipal()
            .flatMap(principal -> userService.findActiveByEmail(principal.getName()))
            .map(user -> user.isMustChangePassword());
        if (mustChangePassword.orElse(false)) {
            return Publishers.just(HttpResponse.seeOther(URI.create("/change-password")));
        }

        return chain.proceed(request);
    }

    private boolean isAllowedPath(String path) {
        return path.equals("/change-password")
            || path.equals("/logout")
            || path.equals("/login")
            || path.equals("/health")
            || path.startsWith("/css/");
    }
}
