package com.amrshalaby.timesheet.audit;

import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Instant;

@Singleton
public class AuditService {
    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public void record(
        Long actorUserId,
        Long subjectUserId,
        String eventType,
        String entityType,
        Long entityId,
        String detailsJson
    ) {
        AuditEvent event = new AuditEvent();
        event.setEventTime(Instant.now(Clock.systemUTC()));
        event.setActorUserId(actorUserId);
        event.setSubjectUserId(subjectUserId);
        event.setEventType(eventType);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setDetailsJson(detailsJson);
        repository.save(event);
    }
}
