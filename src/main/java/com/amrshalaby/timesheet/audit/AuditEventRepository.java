package com.amrshalaby.timesheet.audit;
import io.micronaut.data.jdbc.annotation.JdbcRepository; import io.micronaut.data.model.query.builder.sql.Dialect; import io.micronaut.data.repository.CrudRepository; import java.util.List;
@JdbcRepository(dialect=Dialect.MYSQL) public interface AuditEventRepository extends CrudRepository<AuditEvent, Long>{ List<AuditEvent> findBySubjectUserId(Long subjectUserId); }
