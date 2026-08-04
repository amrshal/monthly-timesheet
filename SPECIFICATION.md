# Monthly Timesheet Application — Specification

## 1. Purpose

Build a small, self-hosted web application for a business with fewer than ten employees.

The application records one total worked duration per employee per calendar day. Employees do not enter clock-in or clock-out times. They enter only the duration worked.

The primary interface is a whole-month timesheet displayed as a horizontal calendar grid, with one row per week and columns running Monday through Sunday.

A monthly timesheet can be submitted and approved. Managers and administrators may correct timesheets directly, with every privileged change recorded in the audit trail.

---

## 2. Authoritative scope

The application must support:

1. One total duration per employee per day.
2. Optional notes for individual days.
3. Entry against past, current and future dates.
4. A complete calendar month on one screen.
5. Weeks displayed Monday through Sunday.
6. Monthly timesheet submission.
7. Manager and administrator approval.
8. Reopening of submitted timesheets by managers and administrators.
9. Reopening of approved timesheets by administrators.
10. Direct edits by managers and administrators, with a detailed audit trail.
11. User and manager administration.
12. Deployment as a Docker container.
13. DigitalOcean Managed MySQL as the production database.

The first release does not include exports or a public API.

---

## 3. Technology stack

### 3.1 Required technologies

Use:

- Java 21 or later
- Micronaut 4
- Micronaut Security
- Micronaut Data JDBC
- Thymeleaf
- MySQL 8-compatible SQL
- Flyway
- Maven
- Bootstrap or lightweight custom CSS
- HTMX only where it materially simplifies server-rendered interactions
- JUnit 5
- Testcontainers using MySQL
- Docker

### 3.2 Prohibited technologies

Do not use:

- Spring Boot
- Spring Framework
- Hibernate
- JPA
- React
- Angular
- Vue
- Node.js as an application runtime
- A separate frontend application
- Microservices
- H2 as the only integration-test database

### 3.3 Application architecture

Build one server-rendered Micronaut application.

Suggested package structure:

```text
com.example.timesheet
├── Application.java
├── auth
├── user
├── timesheet
├── approval
├── audit
├── admin
├── common
└── configuration
```

Use the following dependency direction:

```text
Controller
    ↓
Application service
    ↓
Repository
    ↓
Database
```

Business rules belong in application services or focused domain classes. Do not put business rules in controllers, repositories or Thymeleaf templates.

---

## 4. Deployment architecture

### 4.1 Production deployment

The production architecture is:

```text
Browser
   |
HTTPS
   |
DigitalOcean-hosted Docker container
   |
TLS database connection
   |
DigitalOcean Managed MySQL
```

The application may be deployed to either:

- DigitalOcean App Platform from a Docker image; or
- A DigitalOcean Droplet running the Docker container.

The application must not assume Docker Compose is available in production.

### 4.2 Docker requirements

Provide a production `Dockerfile` that:

- Uses a multi-stage build or copies a prebuilt runnable JAR into a small runtime image.
- Runs as a non-root user.
- Exposes the configured HTTP port.
- Supports environment-based configuration.
- Includes a health check or exposes an endpoint suitable for an external health check.
- Does not contain credentials or environment-specific configuration.
- Uses a supported Java runtime.
- Starts the Micronaut application directly.

### 4.3 Docker Compose

Docker Compose is optional and is for local development only.

If provided, `compose.yml` should start:

- The application.
- A local MySQL container.

The production application must also run correctly without Docker Compose and connect directly to DigitalOcean Managed MySQL.

### 4.4 Managed database

Production uses DigitalOcean Managed MySQL, not MariaDB.

The application must:

- Use a current MySQL JDBC driver.
- Support MySQL 8 authentication.
- Use TLS for the production database connection.
- Accept the JDBC URL, username and password through environment variables.
- Use a small, configurable connection pool suitable for fewer than ten employees.
- Retry transient database connection failures during startup where supported.
- Run Flyway migrations at startup.
- Avoid requiring database superuser privileges.

Example configuration variables:

```text
JDBC_URL
JDBC_USER
JDBC_PASSWORD
JDBC_MAX_POOL_SIZE
DB_SSL_MODE
```

The exact DigitalOcean connection details must not be committed.

---

## 5. Roles and permissions

The application has three roles:

```java
EMPLOYEE
MANAGER
ADMIN
```

### 5.1 Employee

An employee can:

- Sign in.
- Change their own password.
- View their own monthly timesheets.
- Create a monthly timesheet implicitly by opening and saving an unused month.
- Enter and update their own daily durations while the timesheet is `DRAFT`.
- Add, edit and remove their own daily notes while the timesheet is `DRAFT`.
- Enter durations and notes for future dates and future months.
- Submit their own `DRAFT` timesheet.
- View their submitted and approved timesheets.
- View the audit history exposed to employees for their own timesheets.

An employee cannot:

- View another employee’s timesheet.
- Edit a `SUBMITTED` or `APPROVED` timesheet.
- Reopen a timesheet.
- Approve a timesheet.
- Change their role or assigned manager.

### 5.2 Manager

A manager has all employee capabilities for their own timesheet.

For employees assigned to them, a manager can:

- View all monthly timesheets.
- Create or save a timesheet on behalf of an employee.
- Edit daily durations and notes in any timesheet status.
- Submit a `DRAFT` timesheet.
- Approve a `SUBMITTED` timesheet.
- Reopen a `SUBMITTED` timesheet, returning it to `DRAFT`.
- View the complete audit history of those timesheets.

A manager cannot:

- Manage employees not assigned to them.
- Reopen an `APPROVED` timesheet.
- Delete audit events.
- Alter the recorded actor or timestamp of an audit event.

### 5.3 Administrator

An administrator can:

- Create and maintain users.
- Disable and reactivate users.
- Reset passwords.
- Assign roles.
- Assign employees to managers.
- View all timesheets.
- Create or save a timesheet for any user.
- Edit daily durations and notes in any timesheet status.
- Submit any `DRAFT` timesheet.
- Approve any `SUBMITTED` timesheet.
- Reopen any `SUBMITTED` timesheet to `DRAFT`.
- Reopen any `APPROVED` timesheet to `DRAFT`.
- View the complete audit log.

Administrators cannot delete audit events through the normal application.

### 5.4 Meaning of privileged editing

Managers and administrators may edit a timesheet without first reopening it.

A privileged edit:

- Does not automatically change the timesheet status.
- Must be performed through an authorised server-side operation.
- Must record the actor.
- Must record the date and time.
- Must record every changed daily value.
- Must record before and after duration values.
- Must record before and after note values.
- Must identify the affected employee, month and work date.
- Must be visible in the audit history.

An employee does not need to approve or acknowledge a privileged edit.

This feature is described as a silent edit because it does not require a rejection workflow or employee action. It must never be silent in the audit trail.

---

## 6. Authentication and security

### 6.1 Authentication

Use session-based authentication.

Users sign in using:

- Email address.
- Password.

Email addresses are unique.

Normalise email input in application code for comparisons by trimming whitespace and converting it to lowercase before saving or looking up a user. Do not add an `email_normalized` column.

Passwords must use BCrypt, Argon2 or another modern adaptive password hash supported by Micronaut Security.

Never store or log plain-text passwords.

### 6.2 First administrator

Support creation of the first administrator from environment variables or a documented startup command:

```text
INITIAL_ADMIN_EMAIL
INITIAL_ADMIN_PASSWORD
INITIAL_ADMIN_NAME
```

Create the initial administrator only when the `user` table contains no users.

Do not overwrite an existing user on startup.

### 6.3 Authorisation

All authorisation must be enforced server-side.

Every operation must verify:

- Authentication.
- Role.
- Ownership or manager assignment.
- Current timesheet status.
- Validity of the requested status transition.
- Whether the actor may modify the selected employee.

Never trust user IDs, manager IDs, timesheet IDs, statuses, dates or roles supplied by the browser.

### 6.4 CSRF

Enable CSRF protection for all state-changing browser requests.

### 6.5 Sessions and cookies

Production session cookies must support:

- `HttpOnly`.
- `Secure`.
- An appropriate `SameSite` policy.
- Configurable session timeout.
- Logout that invalidates the session.

### 6.6 General security

Protect against:

- SQL injection.
- Cross-site scripting.
- Cross-site request forgery.
- Insecure direct object references.
- Mass assignment.
- Privilege escalation.
- Session fixation.
- Open redirects.

Do not expose stack traces or database errors in production.

---

## 7. Core domain model

### 7.1 Monthly timesheet

A monthly timesheet belongs to exactly one user and one calendar month.

There must be at most one monthly timesheet for each combination of:

```text
user_id + year + month
```

### 7.2 Daily entry

Each monthly timesheet may contain at most one daily entry per calendar date.

A daily entry contains:

- Work date.
- Duration in minutes.
- Optional note.

It does not contain:

- Start time.
- End time.
- Break times.
- Project.
- Activity.
- Client.
- Billing information.

### 7.3 Duration storage

Store durations as integer minutes.

Examples:

```text
07:30 = 450 minutes
08:00 = 480 minutes
```

Do not store durations as floating-point decimal hours.

### 7.4 Duration input

The canonical input and display format is:

```text
HH:MM
```

Examples:

```text
08:00
07:30
04:15
00:00
```

The parser may accept:

```text
8
8:00
7:30
```

After saving, display the canonical `HH:MM` form.

Do not use an HTML time-of-day control, because the value is a duration rather than a clock time.

### 7.5 Duration validation

For each date:

- Minimum: 0 minutes.
- Maximum: 1,440 minutes.
- Negative values are invalid.
- Values above 24 hours are invalid.
- Invalid text is rejected.
- Blank means no duration recorded.
- `00:00` may be stored where a note exists; otherwise it may be treated as an empty entry.

### 7.6 Notes

A note:

- Is optional.
- Belongs to one daily entry.
- Has a maximum length of 1,000 characters.
- Is edited through a note control associated with the day.
- Must be HTML-escaped when rendered.
- Must remain visible through a clear note-present indicator.

---

## 8. Timesheet statuses and transitions

Use exactly these statuses:

```java
public enum TimesheetStatus {
    DRAFT,
    SUBMITTED,
    APPROVED
}
```

There is no `REJECTED` status.

### 8.1 Valid transitions

```text
DRAFT     → SUBMITTED
SUBMITTED → APPROVED
SUBMITTED → DRAFT       manager or administrator reopen
APPROVED  → DRAFT       administrator reopen only
```

No other transition is valid.

### 8.2 DRAFT

A draft timesheet:

- Is editable by its employee owner.
- Is editable by the assigned manager.
- Is editable by an administrator.
- Can be submitted by the employee, assigned manager or administrator.
- Cannot be approved directly.

### 8.3 SUBMITTED

A submitted timesheet:

- Is read-only for the employee.
- Is editable by the assigned manager and administrator.
- Can be approved by the assigned manager or administrator.
- Can be reopened to `DRAFT` by the assigned manager or administrator.
- Cannot be submitted again.

### 8.4 APPROVED

An approved timesheet:

- Is read-only for the employee.
- May be edited by the assigned manager or administrator without changing status.
- Cannot be reopened by a manager.
- Can be reopened to `DRAFT` by an administrator.
- Cannot be approved again.

### 8.5 Reopening

Reopening means returning a timesheet to `DRAFT` so the employee can edit it.

A reopening action must:

- Require a non-blank reason.
- Record the previous status.
- Record the new status.
- Record the actor and timestamp.
- Record the reason.
- Preserve all time entries and notes.

---

## 9. Monthly desktop timesheet layout

### 9.1 Required structure

The desktop view must be horizontal.

Each row represents one Monday-to-Sunday week.

Each day is a separate column.

The primary grid is:

| Week | Monday | Tuesday | Wednesday | Thursday | Friday | Saturday | Sunday | Total |
|---|---|---|---|---|---|---|---|---|

Each day cell contains:

1. Day number and abbreviated day label where useful.
2. Duration textbox.
3. Note icon button.
4. A visible indication when a note exists.

### 9.2 Month boundaries

The displayed grid must contain complete Monday-to-Sunday rows.

Dates outside the selected month may appear in the first and last week to preserve full weeks.

Outside-month cells must:

- Be visually muted.
- Be non-editable.
- Not contribute to the selected month’s total.
- Clearly display their date so the weekly structure remains understandable.

Example for a month starting on Wednesday:

```text
Week 1: Mon(previous month), Tue(previous month), Wed 1, Thu 2, Fri 3, Sat 4, Sun 5
```

### 9.3 Week numbering

A separate week-number column is optional.

If displayed, use ISO week numbering.

The functional requirement is Monday-to-Sunday grouping, not the display of a week number.

### 9.4 Daily duration field

Each editable in-month day cell must contain a compact duration textbox.

The input must:

- Be keyboard accessible.
- Support tabbing in calendar order.
- Retain invalid user input when validation fails.
- Show a field-level validation error.
- Be disabled or rendered read-only when the current user cannot edit.

### 9.5 Notes interaction

Each day must have a note icon next to the duration textbox.

Clicking the icon should open a small accessible modal, popover or expandable editor for that day’s note.

Requirements:

- The associated date must be clear.
- The current note must be loaded.
- The user can save or clear the note.
- The control must be keyboard accessible.
- The note text must not be permanently displayed inside the grid.
- A day with a non-blank note must show a distinct filled icon, badge, dot or equivalent indication.
- A day without a note must show the default unfilled note icon.
- The indication must not rely only on colour.
- A tooltip or accessible label should indicate whether a note exists.

### 9.6 Totals

Show:

- Total per week.
- Total for the month.

Totals must update after a successful save.

Client-side preview calculation is optional, but server-calculated values are authoritative.

### 9.7 Navigation

Include:

- Previous month.
- Next month.
- Current month or Today.
- Month and year heading.
- Status badge.
- Save.
- Submit when permitted.
- Reopen when permitted.
- Approve when permitted.

Do not paginate by week.

### 9.8 Weekends and current day

- Saturday and Sunday remain editable where permissions allow.
- Weekend columns should be visually distinguishable.
- Highlight the current day when viewing the current month.
- Do not use colour as the only indicator.

### 9.9 Responsive behaviour

The desktop layout is authoritative.

On narrower screens:

- Preserve the Monday-to-Sunday sequence.
- Allow horizontal scrolling where necessary.
- Keep row and column headers understandable.
- Do not replace the month with separate weekly pages.

---

## 10. Saving and editing

### 10.1 Employee save

An employee may save only their own `DRAFT` timesheet.

Saving must:

- Validate all edited durations.
- Validate notes.
- Run in a transaction.
- Use optimistic locking.
- Recalculate totals.
- Preserve entered values if validation fails.
- Use POST-Redirect-GET after success.

### 10.2 Manager and administrator save

Managers and administrators use the same monthly screen or an equivalent management view.

When a privileged actor saves changes:

- Verify scope and authorisation.
- Permit edits regardless of current status.
- Keep the existing status unchanged.
- Write the data and audit events in one transaction.
- Record before and after values only for fields that changed.
- Record who made the change and on whose behalf.

### 10.3 Audit granularity for edits

For a privileged edit, create either:

- One audit event per changed day; or
- One batch audit event containing a structured array of changed days.

The audit data must clearly expose, for each changed day:

```text
work_date
duration_before
duration_after
note_before
note_after
```

Employee draft saves do not require before-and-after audit events for every day, but normal creation and update timestamps must be retained.

---

## 11. Submission and approval workflows

### 11.1 Submission

A `DRAFT` timesheet may be submitted by:

- Its employee owner.
- The assigned manager.
- An administrator.

Submission must:

- Validate all entries.
- Change status atomically from `DRAFT` to `SUBMITTED`.
- Record `submitted_at`.
- Record `submitted_by_user_id`.
- Create an audit event.
- Make the timesheet read-only to the employee.

Show a confirmation containing:

- Employee name.
- Month.
- Monthly total.
- Number of days with recorded time.
- A warning that employee editing will be locked.

A zero-hour month may be submitted after an explicit warning.

### 11.2 Approval

A `SUBMITTED` timesheet may be approved by:

- The assigned manager.
- An administrator.

Approval must:

- Atomically verify the current status is `SUBMITTED`.
- Change status to `APPROVED`.
- Record `approved_at`.
- Record `approved_by_user_id`.
- Create an audit event.
- Prevent duplicate approval.

### 11.3 Reopening a submitted timesheet

An assigned manager or administrator may reopen a `SUBMITTED` timesheet.

The action must:

- Require a reason.
- Change status to `DRAFT`.
- Preserve existing entries.
- Clear or retain submission metadata according to the following rule:
  - Preserve historical submission details in audit records.
  - Set current `submitted_at` and `submitted_by_user_id` to null.
- Create an audit event.

### 11.4 Reopening an approved timesheet

Only an administrator may reopen an `APPROVED` timesheet.

The action must:

- Require a reason.
- Change status to `DRAFT`.
- Preserve all entries.
- Preserve approval history in audit records.
- Set current submission and approval fields to null.
- Create an audit event containing previous approval details.

---

## 12. Manager dashboard

The manager dashboard must show timesheets for assigned employees.

### 12.1 Awaiting approval

Display submitted timesheets with:

- Employee name.
- Month.
- Total duration.
- Submitted date.
- Submitted by.
- Review action.

Sort oldest submissions first by default.

### 12.2 Other timesheets

Allow filtering by:

- Employee.
- Month.
- Year.
- Status.

Managers must be able to open drafts, submitted timesheets and approved timesheets for assigned employees.

### 12.3 Management actions

From the timesheet view, show only authorised actions:

- Save privileged edits.
- Submit draft.
- Approve submitted.
- Reopen submitted.

The server must independently enforce all permissions.

---

## 13. Administrator functionality

### 13.1 User management

Administrators can:

- Create users.
- Edit display name and email.
- Assign roles.
- Assign or change manager.
- Disable or reactivate a user.
- Set a temporary password.
- Require password change at next login.

Users should be disabled rather than deleted.

Historical records must remain associated with disabled users.

### 13.2 Timesheet management

Administrators can:

- Find any timesheet.
- Edit any timesheet.
- Submit any draft.
- Approve any submitted timesheet.
- Reopen submitted or approved timesheets.
- View complete audit history.

### 13.3 Audit log

Provide an administrator audit log screen with filters for:

- Actor.
- Subject employee.
- Event type.
- Timesheet.
- Date range.

---

## 14. Database schema

Use Flyway migrations.

### 14.1 Table `user`

The table name must be exactly:

```sql
`user`
```

Because `USER` has special meaning in MySQL contexts, always quote the table name with backticks in SQL migrations and handwritten SQL. Configure Micronaut Data mapping explicitly to the table name `user`.

Columns:

```text
id                       BIGINT PRIMARY KEY AUTO_INCREMENT
email                    VARCHAR(320) NOT NULL
display_name             VARCHAR(200) NOT NULL
password_hash            VARCHAR(255) NOT NULL
role                     VARCHAR(30) NOT NULL
manager_id               BIGINT NULL
active                   BOOLEAN NOT NULL DEFAULT TRUE
must_change_password     BOOLEAN NOT NULL DEFAULT TRUE
created_at               DATETIME(6) NOT NULL
updated_at               DATETIME(6) NOT NULL
version                  BIGINT NOT NULL DEFAULT 0
```

Constraints:

- Unique `email`.
- `manager_id` references ``user`.`id``.
- A user cannot manage themselves, enforced by service validation.
- Valid roles are `EMPLOYEE`, `MANAGER`, `ADMIN`.
- Application code lowercases and trims email addresses before persistence.

### 14.2 Table `monthly_timesheet`

```text
id                       BIGINT PRIMARY KEY AUTO_INCREMENT
user_id                  BIGINT NOT NULL
timesheet_year           SMALLINT NOT NULL
timesheet_month          TINYINT NOT NULL
status                   VARCHAR(30) NOT NULL
submitted_at             DATETIME(6) NULL
submitted_by_user_id     BIGINT NULL
approved_at              DATETIME(6) NULL
approved_by_user_id      BIGINT NULL
created_at               DATETIME(6) NOT NULL
updated_at               DATETIME(6) NOT NULL
version                  BIGINT NOT NULL DEFAULT 0
```

Constraints:

- Foreign key to `user`.
- Unique `(user_id, timesheet_year, timesheet_month)`.
- Month from 1 to 12.
- Status limited to `DRAFT`, `SUBMITTED`, `APPROVED`.
- Optimistic-lock version.
- No `last_rejection_reason` column.

### 14.3 Table `daily_time_entry`

```text
id                       BIGINT PRIMARY KEY AUTO_INCREMENT
timesheet_id             BIGINT NOT NULL
work_date                DATE NOT NULL
duration_minutes         INT NOT NULL
note                     VARCHAR(1000) NULL
created_at               DATETIME(6) NOT NULL
updated_at               DATETIME(6) NOT NULL
version                  BIGINT NOT NULL DEFAULT 0
```

Constraints:

- Foreign key to `monthly_timesheet`.
- Unique `(timesheet_id, work_date)`.
- Duration from 0 to 1,440.
- Work date must belong to the timesheet month, enforced by the service layer.
- Optimistic-lock version.

### 14.4 Table `audit_event`

```text
id                       BIGINT PRIMARY KEY AUTO_INCREMENT
event_time               DATETIME(6) NOT NULL
actor_user_id            BIGINT NULL
subject_user_id          BIGINT NULL
event_type               VARCHAR(100) NOT NULL
entity_type              VARCHAR(100) NOT NULL
entity_id                BIGINT NULL
ip_address               VARCHAR(64) NULL
details_json             JSON NULL
```

Suggested event types:

```text
USER_CREATED
USER_UPDATED
USER_DISABLED
USER_REACTIVATED
USER_ROLE_CHANGED
USER_MANAGER_CHANGED
PASSWORD_RESET
TIMESHEET_CREATED
TIMESHEET_SUBMITTED
TIMESHEET_APPROVED
TIMESHEET_REOPENED
TIMESHEET_PRIVILEGED_EDITED
LOGIN_SUCCEEDED
LOGIN_FAILED
```

Do not store passwords, password hashes, session IDs or CSRF tokens in audit details.

---

## 15. Transactions and concurrency

Use transactions for:

- Saving a monthly timesheet.
- Privileged editing.
- Submission.
- Approval.
- Reopening.
- User creation.
- Role changes.
- Manager assignment.
- User disabling and reactivation.

Use optimistic locking on mutable domain tables.

If a conflicting update occurs:

- Do not overwrite newer data.
- Return a user-friendly conflict response.
- Ask the user to reload.
- Log the conflict appropriately.

Approval must use an atomic status check so the same timesheet cannot be approved twice.

Audit data and its associated business change must commit or roll back together.

---

## 16. Routes

Equivalent route names are acceptable, but preserve the capabilities and HTTP semantics.

### 16.1 Authentication

```text
GET  /login
POST /login
POST /logout
GET  /change-password
POST /change-password
```

### 16.2 Employee timesheets

```text
GET  /timesheets
GET  /timesheets/{year}/{month}
POST /timesheets/{year}/{month}
POST /timesheets/{year}/{month}/submit
```

### 16.3 Manager

```text
GET  /manager
GET  /manager/timesheets
GET  /manager/timesheets/{timesheetId}
POST /manager/timesheets/{timesheetId}
POST /manager/timesheets/{timesheetId}/submit
POST /manager/timesheets/{timesheetId}/approve
POST /manager/timesheets/{timesheetId}/reopen
```

### 16.4 Administrator

```text
GET  /admin/users
GET  /admin/users/new
POST /admin/users
GET  /admin/users/{userId}
POST /admin/users/{userId}
POST /admin/users/{userId}/disable
POST /admin/users/{userId}/reactivate
POST /admin/users/{userId}/reset-password

GET  /admin/timesheets
GET  /admin/timesheets/{timesheetId}
POST /admin/timesheets/{timesheetId}
POST /admin/timesheets/{timesheetId}/submit
POST /admin/timesheets/{timesheetId}/approve
POST /admin/timesheets/{timesheetId}/reopen

GET  /admin/audit
```

State-changing operations must not use GET.

---

## 17. Validation

### 17.1 User validation

Validate:

- Display name is required.
- Email is valid.
- Email is trimmed and lowercased before persistence.
- Email is unique.
- Password meets a configurable minimum length.
- Role is valid.
- Manager assignment is valid.
- A user cannot manage themselves.

### 17.2 Timesheet validation

Validate:

- Year is within a reasonable supported range.
- Month is 1 through 12.
- Work date belongs to the selected month.
- Duration is valid.
- Note length is valid.
- Reopening reason is non-blank.
- Status transition is valid.
- Current actor is authorised.
- Manager is assigned to the employee.

### 17.3 Error display

Return understandable field-level messages.

Do not expose internal exceptions.

Example:

```text
“25:00” is not valid. Enter a duration from 00:00 to 24:00.
```

---

## 18. Audit requirements

Audit records must answer:

- Who performed the action?
- What did they do?
- Which employee was affected?
- Which timesheet and dates were affected?
- When did it happen?
- What values changed?
- Why was a timesheet reopened?

Audit events are required for:

- User creation.
- User updates.
- Role changes.
- Manager assignment changes.
- User disabling or reactivation.
- Administrator password reset.
- Submission.
- Approval.
- Reopening.
- Every manager or administrator timesheet edit.
- Authentication failures.

Audit events must be immutable through normal application operations.

---

## 19. Dates, timestamps and timezone

Use `LocalDate` for worked dates.

Store workflow and audit timestamps in UTC.

Display timestamps in the configured business timezone.

Configuration:

```yaml
app:
  timezone: Europe/London
```

Default to `Europe/London`, but allow an environment variable override.

Month and week calculations must use the business timezone and ISO Monday-first weeks.

---

## 20. Accessibility and usability

The application must:

- Use semantic HTML.
- Use associated labels for every input.
- Be keyboard navigable.
- Provide visible focus states.
- Use sufficient contrast.
- Avoid colour-only status indicators.
- Associate validation errors with fields.
- Use accessible modal or popover behaviour for notes.
- Confirm submission, approval and reopening.
- Display status as text: Draft, Submitted or Approved.

---

## 21. Configuration

Externalise configuration.

Required or supported variables:

```text
JDBC_URL
JDBC_USER
JDBC_PASSWORD
JDBC_MAX_POOL_SIZE
DB_SSL_MODE
SESSION_SECRET
APP_TIMEZONE
MICRONAUT_ENVIRONMENTS
INITIAL_ADMIN_EMAIL
INITIAL_ADMIN_PASSWORD
INITIAL_ADMIN_NAME
HTTP_PORT
```

Provide:

- `application.yml`.
- `application-test.yml`.
- `.env.example`.

Do not commit a real `.env` file or credentials.

---

## 22. Flyway migrations

All schema changes use Flyway.

Requirements:

- Start successfully against an empty MySQL database.
- Preserve migration history.
- Do not use Hibernate schema generation.
- Do not seed demo users in production.
- Keep test fixtures outside production migrations.

Suggested migrations:

```text
V1__create_user.sql
V2__create_monthly_timesheet.sql
V3__create_daily_time_entry.sql
V4__create_audit_event.sql
V5__add_indexes.sql
```

---

## 23. Testing

### 23.1 Unit tests

Test:

- Duration parsing and formatting.
- Month grid generation.
- Monday-to-Sunday week grouping.
- Outside-month cells.
- Monthly and weekly totals.
- Status transitions.
- Submission permissions.
- Approval permissions.
- Reopening permissions.
- Manager scope rules.
- Audit diff generation.
- Email normalisation in application code.

### 23.2 Database tests

Use MySQL Testcontainers.

Test:

- All Flyway migrations.
- Unique email.
- Unique user/month.
- Unique daily date.
- Foreign keys.
- Repository queries.
- Optimistic locking.
- JSON audit details.
- Compatibility with MySQL rather than only H2.

### 23.3 Security tests

Test:

- Anonymous users are redirected or denied.
- Employees cannot access another employee’s timesheet.
- Employees cannot edit submitted or approved timesheets.
- Employees cannot submit another user’s draft.
- Managers cannot access unassigned employees.
- Managers cannot reopen approved timesheets.
- Managers and administrators can edit authorised submitted and approved timesheets as specified.
- Every privileged edit creates an audit event.
- Administrators can reopen approved timesheets.
- CSRF is required.
- Disabled users cannot sign in.
- Manipulated IDs and status values do not bypass authorisation.

### 23.4 End-to-end workflows

Implement tests for:

#### Employee submission and manager approval

1. Administrator creates manager and employee.
2. Employee enters durations and notes.
3. Employee submits.
4. Employee can no longer edit.
5. Manager reviews and approves.
6. Status becomes `APPROVED`.
7. Employee sees approved status.

#### Manager submits employee draft

1. Employee has a draft.
2. Assigned manager opens it.
3. Manager submits it.
4. Manager approves it in a separate operation.
5. Audit records both actions and actors.

#### Reopen submitted

1. Employee submits.
2. Manager reopens with a reason.
3. Status becomes `DRAFT`.
4. Employee can edit again.
5. Audit records the reason.

#### Reopen approved

1. Manager approves.
2. Manager is denied when attempting to reopen.
3. Administrator reopens with a reason.
4. Status becomes `DRAFT`.
5. Employee can edit.
6. Audit preserves prior approval details.

#### Silent manager edit

1. Employee submits.
2. Manager changes a daily duration and note without reopening.
3. Status remains `SUBMITTED`.
4. Audit contains before and after values.
5. Employee cannot edit.

#### Silent administrator edit of approved timesheet

1. Timesheet is approved.
2. Administrator changes one day.
3. Status remains `APPROVED`.
4. Audit records the precise change and actor.

#### Future entry

1. Employee opens a future month.
2. Employee enters durations.
3. Save succeeds.
4. Reload shows the saved values.

---

## 24. Logging and health

Log:

- Startup.
- Migration success or failure.
- Authentication failures without passwords.
- Unexpected errors.
- Submission, approval and reopening.
- Privileged timesheet edits.
- Administrative changes.

Do not log:

- Passwords.
- Password hashes.
- Session cookies.
- CSRF tokens.
- JDBC URLs containing passwords.

Expose a health endpoint suitable for DigitalOcean:

```text
/health
```

Do not expose sensitive configuration in health output.

---

## 25. Error handling

Implement central error handling.

Behaviour:

- Validation errors return the form with user-entered values and field errors.
- Forbidden operations return 403 or a safe 404 where appropriate.
- Missing resources return 404.
- Optimistic-lock conflicts return a clear conflict page.
- Unexpected errors return a generic page and log technical details.
- Production responses never include stack traces.

---

## 26. Non-functional requirements

### 26.1 Performance

Target fewer than ten employees.

- No distributed cache is needed.
- No message broker is needed.
- Avoid N+1 queries.
- A monthly page should load comfortably within one second under normal conditions.
- Keep the database connection pool small.

### 26.2 Reliability

- Workflow changes are transactional.
- Use POST-Redirect-GET.
- Prevent duplicate form submission from causing duplicate transitions.
- Preserve data across application restarts.
- Handle transient managed-database interruptions cleanly.

### 26.3 Maintainability

- Keep controllers small.
- Centralise status transitions.
- Centralise authorisation-sensitive business operations.
- Avoid duplicated permission logic.
- Use clear names.
- Add comments only where they explain non-obvious decisions.
- Document assumptions in the README.

---

## 27. Explicitly out of scope

Do not implement:

- Clock-in or clock-out.
- Start and end times.
- Break tracking.
- Projects.
- Activities.
- Clients.
- Billing.
- Expenses.
- Invoicing.
- Payroll calculations.
- Overtime rules.
- Holiday requests.
- Sick leave.
- Public holiday calendars.
- Shift scheduling.
- Multi-company tenancy.
- Native mobile apps.
- SSO.
- Two-factor authentication.
- Email notifications.
- Slack or Teams integration.
- Imports.
- Exports.
- Public API.
- Rejected status.
- Rejection workflow.
- Weekly timesheets.
- Weekly approval.
- PDF generation.

Do not add speculative features.

---

## 28. Required repository contents

```text
pom.xml
README.md
SPECIFICATION.md
AGENTS.md
Dockerfile
compose.yml              optional, local development only
.env.example
src/main/java/...
src/main/resources/application.yml
src/main/resources/views/...
src/main/resources/static/...
src/main/resources/db/migration/...
src/test/java/...
```

The README must include:

1. Product overview.
2. Architecture.
3. Prerequisites.
4. Local development.
5. Tests.
6. Docker image build and run.
7. Optional local Compose instructions.
8. DigitalOcean deployment guidance.
9. Managed MySQL configuration.
10. TLS configuration.
11. First administrator creation.
12. Database backup and restore using DigitalOcean facilities and logical dumps.
13. Known limitations.
14. Security notes.

---

## 29. Acceptance criteria

The application is complete only when:

### Employee experience

- An employee can open any month.
- The full month is displayed on one screen.
- Each row is a Monday-to-Sunday week.
- Each in-month day has a duration field and note icon.
- A visible indicator shows when a note exists.
- Future entries work.
- Weekend entries work.
- Weekly and monthly totals are correct.
- Submitted and approved timesheets are read-only to employees.

### Workflow

- Employee, assigned manager and administrator can submit a draft.
- Assigned manager and administrator can approve a submitted timesheet.
- Assigned manager and administrator can reopen a submitted timesheet.
- Only administrator can reopen an approved timesheet.
- Reopening returns the timesheet to draft.
- Reopening requires a reason.
- No rejected status exists.

### Privileged editing

- Assigned managers and administrators can edit authorised timesheets in all statuses.
- Privileged editing does not automatically change status.
- Every changed duration and note is audited with before and after values.
- Unauthorised users cannot perform or view privileged edits.

### Security

- Server-side authorisation covers every operation.
- CSRF protection is tested.
- Direct ID manipulation does not expose another employee’s data.
- Passwords are safely hashed.
- Disabled users cannot sign in.
- No sensitive values are logged.

### Deployment

- Maven tests pass from a clean checkout.
- The Docker image builds.
- The container starts without Docker Compose.
- The application connects to DigitalOcean Managed MySQL using environment variables and TLS.
- Flyway creates the schema on an empty MySQL database.
- `/health` works.
- Restarting the application does not lose data.

---

## 30. Suggested implementation order

### Phase 1 — Foundation

- Scaffold Micronaut Maven project.
- Add Micronaut Security, Data JDBC, Thymeleaf, Flyway and MySQL.
- Add Testcontainers.
- Add configuration and health.
- Add Dockerfile.

### Phase 2 — Users and authentication

- Create `user` migration.
- Implement authentication.
- Implement roles.
- Create first-administrator bootstrapping.
- Implement password change.
- Add security tests.

### Phase 3 — Timesheet model

- Add monthly timesheet, daily entry and audit migrations.
- Implement repositories.
- Implement duration parser.
- Implement Monday-to-Sunday month grid.
- Implement status transition service.
- Add unit and database tests.

### Phase 4 — Employee UI

- Implement horizontal monthly grid.
- Implement duration editing.
- Implement note icon and note editor.
- Implement note-present indication.
- Implement save and totals.
- Add responsive behaviour.

### Phase 5 — Workflow

- Implement submission.
- Implement manager dashboard.
- Implement approval.
- Implement reopening.
- Implement audit events.
- Add workflow and concurrency tests.

### Phase 6 — Privileged editing

- Implement manager/admin editing.
- Implement audit diff generation.
- Preserve status during edits.
- Test submitted and approved edits.
- Test scope restrictions.

### Phase 7 — Administration

- Implement user maintenance.
- Implement manager assignment.
- Implement disabling/reactivation.
- Implement password reset.
- Implement audit log browser.

### Phase 8 — Hardening and deployment

- Review every route for authorisation and CSRF.
- Run all tests against MySQL Testcontainers.
- Build and run the Docker image.
- Test against an empty MySQL database.
- Document DigitalOcean App Platform and Droplet deployment.
- Complete README.
