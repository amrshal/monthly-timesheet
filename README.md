# Monthly Timesheet

Server-rendered monthly timesheet application for fewer than ten employees.

## Stack

- Java 25
- Micronaut 5.1.0
- Maven
- Thymeleaf
- Micronaut Data JDBC
- Flyway
- MySQL 8 / DigitalOcean Managed MySQL
- MySQL Testcontainers for integration testing
- Docker

## Current implementation notes

This branch lays down the application foundation: Micronaut configuration, Flyway schema,
core duration/month-grid/status policy code, server-rendered pages, Docker assets,
basic user administration, authentication, employee/manager/admin timesheet workflows,
and focused unit tests. The remaining production hardening should continue in small slices
following `SPECIFICATION.md`.

Decisions made while implementing without further input:

- Base package: `com.amrshalaby.timesheet`.
- Default business timezone: `Europe/London`.
- Styling: Bootstrap-compatible custom CSS variables plus small monthly-grid CSS.
- HTMX: not used; vanilla JavaScript only where needed.
- Privileged edit audit plan: one batch `TIMESHEET_PRIVILEGED_EDITED` event with a JSON `changed_days` array.
- User registration: no public self-registration; administrators create users.
- Manager-created employees are disabled by default and can be enabled with
  `APP_MANAGER_USER_CREATION_ENABLED=true` if the product decision changes.
- Password minimum length defaults to 12 characters and can be changed with
  `PASSWORD_MINIMUM_LENGTH`.
- Local Docker Compose is provided only for development.

## Local development

```bash
mvn test
```

The MySQL Testcontainers migration test runs automatically when Docker is available.
When Docker is unavailable, JUnit skips that test and the regular unit tests still run.

Run with local MySQL:

```bash
docker compose up --build
```

Open:

```text
http://localhost:8080/health
```

## Production package

```bash
mvn -DskipTests package
```

## Docker image

```bash
docker build -t monthly-timesheet:latest .
```

## DigitalOcean App Platform

1. Build and push the Docker image to your registry.
2. Create an App Platform app from the image.
3. Configure environment variables from `.env.example`.
4. Use a DigitalOcean Managed MySQL database and set the JDBC URL with TLS enabled, for example `sslMode=REQUIRED`.
5. Configure `/health` as the health endpoint.

Do not commit DigitalOcean credentials, database certificates, or real `.env` files.

## DigitalOcean Droplet with docker run

```bash
docker run -d \
  --name monthly-timesheet \
  --restart unless-stopped \
  -p 8080:8080 \
  -e JDBC_URL='jdbc:mysql://your-do-host:25060/monthly_timesheet?sslMode=REQUIRED' \
  -e JDBC_USER='timesheet_app' \
  -e JDBC_PASSWORD='replace-me' \
  -e JDBC_MAX_POOL_SIZE='5' \
  -e DB_SSL_MODE='REQUIRED' \
  -e SESSION_SECRET='replace-with-long-random-secret' \
  -e APP_TIMEZONE='Europe/London' \
  -e INITIAL_ADMIN_EMAIL='admin@example.com' \
  -e INITIAL_ADMIN_PASSWORD='replace-with-temporary-password' \
  -e INITIAL_ADMIN_NAME='Initial Administrator' \
  monthly-timesheet:latest
```

## Known limitations in this implementation slice

- Field-level validation currently covers monthly timesheet entry errors; user administration errors still use a generic error page.
- End-to-end security tests for CSRF, direct object-reference attacks, disabled users, and complete workflow scenarios are still incomplete.
- Admin audit filtering exists for actor, subject, event type, timesheet, and date range, but is intentionally simple and should move to repository queries if the log grows.
- Docker image startup against a clean external MySQL instance still needs to be exercised before calling the application complete.

## Backup and restore

Use DigitalOcean Managed MySQL backups for routine recovery. For logical backups, run
`mysqldump` from a trusted machine with TLS enabled and restore with the matching `mysql`
client command. Do not store dumps containing production personal data or credentials in
the repository.
