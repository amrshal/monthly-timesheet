# AGENTS.md

## Mission

Build the monthly timesheet application defined in `SPECIFICATION.md`.

`SPECIFICATION.md` is authoritative. Read it completely before changing code.

The application is a small server-rendered Micronaut application using Java, Thymeleaf, Micronaut Data JDBC, Flyway and MySQL. It is deployed as a Docker container and must connect to DigitalOcean Managed MySQL without requiring Docker Compose.

---

## Non-negotiable constraints

- Use Micronaut 5.
- Use Java 25.
- Use Maven.
- Use Thymeleaf.
- Use Micronaut Data JDBC.
- Use Flyway.
- Use MySQL-compatible SQL.
- Use MySQL Testcontainers for integration tests.
- Use a production Dockerfile.
- Do not use Spring Boot or Spring Framework.
- Do not use Hibernate or JPA.
- Do not use React, Angular or Vue.
- Do not build a separate frontend.
- Do not introduce microservices.
- Do not implement exports or a public API.
- Do not add projects, activities, timers, start/end times or payroll features.
- Do not add a `REJECTED` status.
- Do not convert the monthly interface into weekly pages.

---

## Domain rules

There is one total worked duration per employee per day.

Durations:

- Are entered as durations, not times of day.
- Are stored as integer minutes.
- Use `HH:MM` as the canonical display format.
- May be entered for future dates.
- Must be between zero and 1,440 minutes.

Timesheet statuses are exactly:

```java
DRAFT
SUBMITTED
APPROVED
```

Valid transitions are exactly:

```text
DRAFT     → SUBMITTED
SUBMITTED → APPROVED
SUBMITTED → DRAFT       manager or administrator
APPROVED  → DRAFT       administrator only
```

Managers and administrators may edit authorised timesheets in any status. Such edits:

- Do not automatically change status.
- Must create an immutable audit record.
- Must record before and after values for every changed day and note.

Reopening is distinct from privileged editing. Reopening returns the timesheet to `DRAFT` so the employee can edit it.

Managers and administrators may submit drafts.

Managers and administrators may approve submitted timesheets.

Managers are limited to employees assigned to them.

---

## UI rules

The desktop timesheet is a horizontal monthly calendar grid.

- One row per Monday-to-Sunday week.
- Monday is the first day column.
- Sunday is the last day column.
- Include complete week rows.
- Dates outside the selected month are muted and non-editable.
- Each in-month day has a duration textbox.
- Each day has a note icon beside the duration.
- Clicking the icon opens an accessible note editor.
- A visible, non-colour-only indication shows that a note exists.
- Show weekly totals and a monthly total.
- Do not paginate the month by week.
- On narrow screens, horizontal scrolling is acceptable.

---

## Database rules

The user table must be named exactly:

```sql
`user`
```

Always quote it in handwritten SQL because `USER` has special meaning in MySQL contexts.

Do not add `email_normalized`.

The `email` column itself is unique. Application code must trim and lowercase emails before saving and lookup.

`monthly_timesheet` must not contain `last_rejection_reason`.

Use Flyway for all schema changes. Never rely on automatic schema creation.

---

## Security rules

All authorisation is server-side.

Never rely on hidden controls or client-supplied IDs, roles or statuses.

Protect all state-changing browser operations with CSRF.

Test for insecure direct object references.

Passwords must be adaptively hashed.

Do not log:

- Passwords.
- Password hashes.
- Session cookies.
- CSRF tokens.
- Credentials.
- JDBC URLs containing passwords.

Privileged edits, submission, approval and reopening must write their audit records in the same database transaction as the business change.

---

## Coding approach

Prefer straightforward code over framework tricks.

Use this dependency direction:

```text
Controller → application service → repository
```

Keep:

- Controllers small.
- Business rules centralised.
- Status transitions in one service or domain component.
- Authorisation-sensitive operations in services with explicit actor and subject parameters.
- Templates free of business logic.
- Repository methods focused on persistence.

Do not duplicate status or permission rules across controllers.

Use immutable request/command objects where practical.

Use `LocalDate` for worked dates.

Store audit and workflow timestamps in UTC.

Use optimistic locking on mutable business records.

Use POST-Redirect-GET after successful form actions.

---

## Testing expectations

Do not mark a task complete with failing tests.

At minimum, maintain tests for:

- Duration parsing and formatting.
- Monday-to-Sunday month grid generation.
- Weekly and monthly totals.
- Valid and invalid status transitions.
- Employee ownership.
- Manager assignment scope.
- Administrator permissions.
- Employee inability to edit submitted or approved timesheets.
- Manager/admin privileged edits in all statuses.
- Audit before-and-after values.
- Reopening submitted timesheets.
- Administrator-only reopening of approved timesheets.
- Submission by employee, manager and administrator.
- Approval by assigned manager and administrator.
- CSRF.
- Direct object-reference attacks.
- Optimistic-lock conflicts.
- Flyway migrations on MySQL.
- Unique database constraints.

Use MySQL Testcontainers for repository and integration behaviour. H2 may be used only for narrowly scoped tests where MySQL behaviour is irrelevant, and must not be the sole integration database.

---

## Deployment expectations

The production artefact is a Docker image.

The container must:

- Run as a non-root user.
- Start the Micronaut application directly.
- Accept configuration through environment variables.
- Connect to an external MySQL database over TLS.
- Expose `/health`.
- Work without Docker Compose.

Docker Compose, if added, is only a convenience for local development.

Document both:

- DigitalOcean App Platform deployment from a container image.
- DigitalOcean Droplet deployment using `docker run`.

Do not place DigitalOcean credentials or database certificates in the repository.

---

## Work sequence

Follow the implementation phases in `SPECIFICATION.md`.

For each substantial phase:

1. Inspect existing code before editing.
2. State a concise implementation plan in the task log or PR description.
3. Implement the smallest complete slice.
4. Add or update tests.
5. Run relevant focused tests.
6. Run the full Maven test suite before declaring completion.
7. Update documentation when commands or behaviour change.

Do not perform broad unrelated refactoring.

Do not add speculative features.

---

## Completion checks

Before claiming the application is complete:

1. Run `./mvnw test` or `mvn test`.
2. Run the production package build.
3. Build the Docker image.
4. Start the container against a clean MySQL instance.
5. Verify Flyway applies all migrations.
6. Verify first-administrator creation.
7. Exercise employee entry and submission.
8. Exercise manager privileged editing and audit output.
9. Exercise manager approval.
10. Exercise manager reopening of submitted timesheets.
11. Verify manager cannot reopen an approved timesheet.
12. Exercise administrator reopening of an approved timesheet.
13. Verify employee and manager scope protections with direct HTTP requests.
14. Verify `/health`.
15. Update `README.md` with exact commands and known limitations.

Report incomplete requirements honestly. Do not hide failing tests, skipped security checks or unimplemented acceptance criteria.
