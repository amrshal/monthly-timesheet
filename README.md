# Monthly Timesheet

Server-rendered monthly timesheet application for fewer than ten employees.

## Stack

- Java 21
- Micronaut 4.10.17 platform line
- Maven
- Thymeleaf
- Micronaut Data JDBC
- Flyway
- MySQL 8 / DigitalOcean Managed MySQL
- MySQL Testcontainers for integration testing
- Docker

## Version note

Micronaut Framework 5.1.0 is the latest overall Micronaut major release as of August 2026.
This project remains on the latest Micronaut 4 platform line because `SPECIFICATION.md`
requires Micronaut 4.

## Current implementation notes

This branch lays down the application foundation: Micronaut configuration, Flyway schema,
core duration/month-grid/status policy code, server-rendered placeholder pages, Docker assets,
and focused unit tests. The remaining production workflows should continue in small slices
following `SPECIFICATION.md`.

Decisions made while implementing without further input:

- Base package: `com.amrshalaby.timesheet`.
- Default business timezone: `Europe/London`.
- Styling: Bootstrap-compatible custom CSS variables plus small monthly-grid CSS.
- HTMX: not used; vanilla JavaScript only where needed.
- Privileged edit audit plan: one batch `TIMESHEET_PRIVILEGED_EDITED` event with a JSON `changed_days` array.
- User registration: no public self-registration; admins and authorised managers create users.
- Local Docker Compose is provided only for development.

## Local development

```bash
mvn test
```

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

- Full authentication provider, password hashing, administrator/manager CRUD screens, monthly
  save workflow, workflow POST actions, and integration/security tests are not yet complete.
- Maven dependency resolution could not complete in the current environment because Maven Central returned HTTP 403 from the network tunnel.
