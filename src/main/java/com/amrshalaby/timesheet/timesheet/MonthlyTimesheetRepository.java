package com.amrshalaby.timesheet.timesheet;

import io.micronaut.core.annotation.Nullable;
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
        SELECT *
        FROM monthly_timesheet
        WHERE user_id IN (:userIds)
          AND status = 'SUBMITTED'
        ORDER BY submitted_at ASC, id ASC
        """)
    List<MonthlyTimesheet> findSubmittedForUsers(List<Long> userIds);

    @Query("""
        SELECT *
        FROM monthly_timesheet
        WHERE user_id IN (:userIds)
          AND (:employeeId IS NULL OR user_id = :employeeId)
          AND (:year IS NULL OR timesheet_year = :year)
          AND (:month IS NULL OR timesheet_month = :month)
          AND (:status IS NULL OR status = :status)
        ORDER BY timesheet_year DESC, timesheet_month DESC, id DESC
        """)
    List<MonthlyTimesheet> findManaged(
        List<Long> userIds,
        @Nullable Long employeeId,
        @Nullable Integer year,
        @Nullable Integer month,
        @Nullable TimesheetStatus status
    );

    @Query("""
        UPDATE monthly_timesheet
        SET status = 'SUBMITTED',
            submitted_at = :submittedAt,
            submitted_by_user_id = :submittedByUserId,
            updated_at = UTC_TIMESTAMP(6),
            version = version + 1
        WHERE id = :id
          AND status = 'DRAFT'
          AND version = :expectedVersion
        """)
    long submitDraft(Long id, Instant submittedAt, Long submittedByUserId, Long expectedVersion);

    @Query("""
        UPDATE monthly_timesheet
        SET status = 'APPROVED',
            approved_at = :approvedAt,
            approved_by_user_id = :approvedByUserId,
            updated_at = UTC_TIMESTAMP(6),
            version = version + 1
        WHERE id = :id
          AND status = 'SUBMITTED'
          AND version = :expectedVersion
        """)
    long approveSubmitted(Long id, Instant approvedAt, Long approvedByUserId, Long expectedVersion);

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
          AND version = :expectedVersion
        """)
    long reopenToDraft(Long id, TimesheetStatus previousStatus, Long expectedVersion);
}
