package com.amrshalaby.timesheet.audit;

import com.amrshalaby.timesheet.common.BusinessTimeFormatter;
import com.amrshalaby.timesheet.user.AppUser;
import com.amrshalaby.timesheet.user.UserService;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
public class AuditViewService {
    private final UserService userService;
    private final BusinessTimeFormatter timeFormatter;

    public AuditViewService(
        UserService userService,
        BusinessTimeFormatter timeFormatter
    ) {
        this.userService = userService;
        this.timeFormatter = timeFormatter;
    }

    public List<AuditEventView> toViews(List<AuditEvent> events) {
        return events.stream().map(this::toView).toList();
    }

    public AuditEventView toView(AuditEvent event) {
        return new AuditEventView(
            timeFormatter.format(event.getEventTime()),
            userLabel(event.getActorUserId()),
            userLabel(event.getSubjectUserId()),
            event.getEventType(),
            event.getEntityType() + ":" + event.getEntityId(),
            readableDetails(event)
        );
    }

    private String userLabel(Long userId) {
        if (userId == null) {
            return "System";
        }

        Optional<AppUser> user = userService.findById(userId);
        return user.map(value -> value.getDisplayName() + " (" + value.getEmail() + ")")
            .orElse("User " + userId);
    }

    private String readableDetails(AuditEvent event) {
        String detailsJson = event.getDetailsJson();
        String details = "";
        if (detailsJson == null || detailsJson.isBlank() || "{}".equals(detailsJson)) {
            details = "";
        } else {
            details = detailsJson;
        }

        if (event.getIpAddress() == null || event.getIpAddress().isBlank()) {
            return details;
        }
        if (details.isBlank()) {
            return "IP address: " + event.getIpAddress();
        }
        return details + " IP address: " + event.getIpAddress();
    }
}
