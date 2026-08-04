package com.amrshalaby.timesheet.user;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface UserRepository extends CrudRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);

    long countByRole(UserRole role);

    List<AppUser> findByManagerId(Long managerId);

    @Query("SELECT COUNT(*) FROM `user`")
    long countUsers();
}
