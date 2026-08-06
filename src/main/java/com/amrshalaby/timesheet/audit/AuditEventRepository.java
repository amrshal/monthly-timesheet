package com.amrshalaby.timesheet.audit;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.time.Instant;
import java.util.List;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface AuditEventRepository extends CrudRepository<AuditEvent, Long> {
    List<AuditEvent> findBySubjectUserId(Long subjectUserId);

    @Query("""
        SELECT *
        FROM audit_event
        WHERE (:actorUserId IS NULL OR actor_user_id = :actorUserId)
          AND (:subjectUserId IS NULL OR subject_user_id = :subjectUserId)
          AND (:eventType IS NULL OR :eventType = '' OR event_type = :eventType)
          AND (:timesheetId IS NULL OR (entity_type = 'monthly_timesheet' AND entity_id = :timesheetId))
          AND (:fromInclusive IS NULL OR event_time >= :fromInclusive)
          AND (:toExclusive IS NULL OR event_time < :toExclusive)
        ORDER BY event_time DESC, id DESC
        LIMIT 500
        """)
    List<AuditEvent> search(
        @Nullable Long actorUserId,
        @Nullable Long subjectUserId,
        @Nullable String eventType,
        @Nullable Long timesheetId,
        @Nullable Instant fromInclusive,
        @Nullable Instant toExclusive
    );
}
