package com.amrshalaby.timesheet.configuration;

import com.amrshalaby.timesheet.user.CreateUserCommand;
import com.amrshalaby.timesheet.user.UserRepository;
import com.amrshalaby.timesheet.user.UserRole;
import com.amrshalaby.timesheet.user.UserService;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Requires(notEnv = "test")
@Requires(property = "app.initial-admin.enabled", notEquals = "false")
public class InitialAdminBootstrap {
    private static final Logger LOG = LoggerFactory.getLogger(InitialAdminBootstrap.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final String email;
    private final String password;
    private final String name;

    public InitialAdminBootstrap(
        UserRepository userRepository,
        UserService userService,
        @Value("${app.initial-admin.email:}") String email,
        @Value("${app.initial-admin.password:}") String password,
        @Value("${app.initial-admin.name:}") String name
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @EventListener
    public void onStartup(ServerStartupEvent event) {
        if (userRepository.countUsers() > 0) {
            return;
        }
        if (email.isBlank() || password.isBlank() || name.isBlank()) {
            LOG.warn("Initial administrator was not created because INITIAL_ADMIN_* variables are incomplete.");
            return;
        }

        userService.createUser(
            null,
            new CreateUserCommand(email, name, password, UserRole.ADMIN, null, true)
        );
        LOG.info("Initial administrator account created from environment configuration.");
    }
}
