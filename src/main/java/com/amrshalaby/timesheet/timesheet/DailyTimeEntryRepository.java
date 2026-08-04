package com.amrshalaby.timesheet.timesheet;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface DailyTimeEntryRepository extends CrudRepository<DailyTimeEntry, Long> {
    List<DailyTimeEntry> findByTimesheetId(Long timesheetId);

    Optional<DailyTimeEntry> findByTimesheetIdAndWorkDate(Long timesheetId, LocalDate workDate);
}
