package com.amrshalaby.timesheet.timesheet;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface MonthlyTimesheetRepository extends CrudRepository<MonthlyTimesheet, Long> {
    Optional<MonthlyTimesheet> findByUserIdAndYearAndMonth(Long userId, int year, int month);

    List<MonthlyTimesheet> findByStatus(TimesheetStatus status);
}
