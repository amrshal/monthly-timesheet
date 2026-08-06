package com.amrshalaby.timesheet.audit;

import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

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
        record(actorUserId, subjectUserId, eventType, entityType, entityId, null, detailsJson);
    }

    public void record(
        Long actorUserId,
        Long subjectUserId,
        String eventType,
        String entityType,
        Long entityId,
        String ipAddress,
        String detailsJson
    ) {
        AuditEvent event = new AuditEvent();
        event.setEventTime(Instant.now(Clock.systemUTC()));
        event.setActorUserId(actorUserId);
        event.setSubjectUserId(subjectUserId);
        event.setEventType(eventType);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setIpAddress(ipAddress);
        event.setDetailsJson(detailsJson);
        repository.save(event);
    }

    public List<AuditEvent> findForTimesheet(Long subjectUserId, Long timesheetId) {
        return StreamSupport.stream(repository.findBySubjectUserId(subjectUserId).spliterator(), false)
            .filter(event -> "monthly_timesheet".equals(event.getEntityType()))
            .filter(event -> timesheetId.equals(event.getEntityId()))
            .sorted(Comparator.comparing(AuditEvent::getEventTime).reversed())
            .toList();
    }
}
