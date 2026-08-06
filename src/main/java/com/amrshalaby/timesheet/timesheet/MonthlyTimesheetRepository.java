package com.amrshalaby.timesheet.timesheet;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface MonthlyTimesheetRepository extends CrudRepository<MonthlyTimesheet, Long> {
    Optional<MonthlyTimesheet> findByUserIdAndYearAndMonth(Long userId, int year, int month);

    List<MonthlyTimesheet> findByStatus(TimesheetStatus status);

    @Query("""
        UPDATE monthly_timesheet
        SET status = 'SUBMITTED',
            submitted_at = :submittedAt,
            submitted_by_user_id = :submittedByUserId,
            updated_at = UTC_TIMESTAMP(6),
            version = version + 1
        WHERE id = :id
          AND status = 'DRAFT'
        """)
    long submitDraft(Long id, Instant submittedAt, Long submittedByUserId);

    @Query("""
        UPDATE monthly_timesheet
        SET status = 'APPROVED',
            approved_at = :approvedAt,
            approved_by_user_id = :approvedByUserId,
            updated_at = UTC_TIMESTAMP(6),
            version = version + 1
        WHERE id = :id
          AND status = 'SUBMITTED'
        """)
    long approveSubmitted(Long id, Instant approvedAt, Long approvedByUserId);

    @Query("""
        UPDATE monthly_timesheet
        SET status = 'DRAFT',
            submitted_at = NULL,
            submitted_by_user_id = NULL,
            approved_at = NULL,
            approved_by_user_id = NULL,
            updated_at = UTC_TIMESTAMP(6),
            version = version + 1
        WHERE id = :id
          AND status = :previousStatus
        """)
    long reopenToDraft(Long id, TimesheetStatus previousStatus);
}
