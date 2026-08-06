package com.amrshalaby.timesheet.database;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationTest {
    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void flywayCreatesSchemaAndDatabaseConstraintsHold() throws SQLException {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        try (Connection connection = DriverManager.getConnection(
            MYSQL.getJdbcUrl(),
            MYSQL.getUsername(),
            MYSQL.getPassword()
        )) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    INSERT INTO `user` (
                        email, display_name, password_hash, role, active, must_change_password, created_at, updated_at
                    ) VALUES (
                        'employee@example.com', 'Employee', 'hash', 'EMPLOYEE', TRUE, TRUE, NOW(6), NOW(6)
                    )
                    """);
                statement.executeUpdate("""
                    INSERT INTO monthly_timesheet (
                        user_id, timesheet_year, timesheet_month, status, created_at, updated_at
                    ) VALUES (
                        1, 2026, 8, 'DRAFT', NOW(6), NOW(6)
                    )
                    """);
                statement.executeUpdate("""
                    INSERT INTO daily_time_entry (
                        timesheet_id, work_date, duration_minutes, note, created_at, updated_at
                    ) VALUES (
                        1, '2026-08-05', 480, JSON_QUOTE('note'), NOW(6), NOW(6)
                    )
                    """);
                statement.executeUpdate("""
                    INSERT INTO audit_event (
                        event_time, actor_user_id, subject_user_id, event_type, entity_type, entity_id, details_json
                    ) VALUES (
                        NOW(6), 1, 1, 'TIMESHEET_CREATED', 'monthly_timesheet', 1, JSON_OBJECT('month', 8)
                    )
                    """);
            }

            assertThrows(SQLException.class, () -> execute(connection, """
                INSERT INTO `user` (
                    email, display_name, password_hash, role, active, must_change_password, created_at, updated_at
                ) VALUES (
                    'employee@example.com', 'Duplicate', 'hash', 'EMPLOYEE', TRUE, TRUE, NOW(6), NOW(6)
                )
                """));
            assertThrows(SQLException.class, () -> execute(connection, """
                INSERT INTO monthly_timesheet (
                    user_id, timesheet_year, timesheet_month, status, created_at, updated_at
                ) VALUES (
                    1, 2026, 8, 'DRAFT', NOW(6), NOW(6)
                )
                """));
            assertThrows(SQLException.class, () -> execute(connection, """
                INSERT INTO daily_time_entry (
                    timesheet_id, work_date, duration_minutes, created_at, updated_at
                ) VALUES (
                    1, '2026-08-06', 1441, NOW(6), NOW(6)
                )
                """));
            assertThrows(SQLException.class, () -> execute(connection, """
                INSERT INTO daily_time_entry (
                    timesheet_id, work_date, duration_minutes, created_at, updated_at
                ) VALUES (
                    1, '2026-08-05', 120, NOW(6), NOW(6)
                )
                """));
            assertThrows(SQLException.class, () -> execute(connection, """
                INSERT INTO monthly_timesheet (
                    user_id, timesheet_year, timesheet_month, status, created_at, updated_at
                ) VALUES (
                    999, 2026, 9, 'DRAFT', NOW(6), NOW(6)
                )
                """));
            assertThrows(SQLException.class, () -> execute(connection, """
                INSERT INTO audit_event (
                    event_time, actor_user_id, subject_user_id, event_type, entity_type, entity_id, details_json
                ) VALUES (
                    NOW(6), 1, 1, 'TIMESHEET_CREATED', 'monthly_timesheet', 1, 'not-json'
                )
                """));
            assertEquals(1, executeUpdate(connection, """
                UPDATE monthly_timesheet
                SET status = 'SUBMITTED',
                    submitted_at = NOW(6),
                    submitted_by_user_id = 1,
                    updated_at = NOW(6),
                    version = version + 1
                WHERE id = 1
                  AND status = 'DRAFT'
                """));
            assertEquals(0, executeUpdate(connection, """
                UPDATE monthly_timesheet
                SET status = 'APPROVED',
                    approved_at = NOW(6),
                    approved_by_user_id = 1,
                    updated_at = NOW(6),
                    version = version + 1
                WHERE id = 1
                  AND status = 'DRAFT'
                """));
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static int executeUpdate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }
}
