package com.amrshalaby.timesheet.timesheet;

import com.amrshalaby.timesheet.audit.AuditEventType;
import com.amrshalaby.timesheet.audit.AuditService;
import com.amrshalaby.timesheet.common.DurationFormat;
import com.amrshalaby.timesheet.user.AppUser;
import com.amrshalaby.timesheet.user.AuthorisationService;
import com.amrshalaby.timesheet.user.UserRole;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

@Singleton
public class TimesheetService {
    private final MonthlyTimesheetRepository timesheetRepository;
    private final DailyTimeEntryRepository entryRepository;
    private final AuditService auditService;
    private final AuthorisationService authorisationService;

    public TimesheetService(
        MonthlyTimesheetRepository timesheetRepository,
        DailyTimeEntryRepository entryRepository,
        AuditService auditService,
        AuthorisationService authorisationService
    ) {
        this.timesheetRepository = timesheetRepository;
        this.entryRepository = entryRepository;
        this.auditService = auditService;
        this.authorisationService = authorisationService;
    }

    @Transactional
    public MonthlyTimesheet getOrCreate(AppUser actor, AppUser subject, int year, int month) {
        authorisationService.requireTimesheetScope(actor, subject);
        validateMonth(year, month);

        return timesheetRepository.findByUserIdAndYearAndMonth(subject.getId(), year, month)
            .orElseGet(() -> createDraft(actor, subject, year, month));
    }

    public List<DailyTimeEntry> entries(Long timesheetId) {
        return entryRepository.findByTimesheetId(timesheetId);
    }

    @Transactional
    public void saveEmployeeDraft(
        AppUser actor,
        AppUser subject,
        MonthlyTimesheet timesheet,
        SaveTimesheetCommand command
    ) {
        if (!StatusTransitionPolicy.employeeCanEdit(timesheet.getStatus(), actor.getId().equals(subject.getId()))) {
            throw new SecurityException("Employees can edit only their own draft timesheets.");
        }

        saveEntries(timesheet, command);
        timesheetRepository.update(timesheet);
    }

    @Transactional
    public void savePrivileged(
        AppUser actor,
        AppUser subject,
        MonthlyTimesheet timesheet,
        SaveTimesheetCommand command
    ) {
        authorisationService.requireTimesheetReviewScope(actor, subject);
        if (!StatusTransitionPolicy.privilegedCanEdit(actor.getRole(), true)) {
            throw new SecurityException("Privileged edit access is required.");
        }

        List<PrivilegedChange> changes = saveEntries(timesheet, command);
        timesheetRepository.update(timesheet);
        if (!changes.isEmpty()) {
            auditService.record(
                actor.getId(),
                subject.getId(),
                AuditEventType.TIMESHEET_PRIVILEGED_EDITED.name(),
                "monthly_timesheet",
                timesheet.getId(),
                privilegedEditDetails(timesheet, changes)
            );
        }
    }

    @Transactional
    public void submit(AppUser actor, AppUser subject, MonthlyTimesheet timesheet) {
        submit(actor, subject, timesheet, timesheet.getVersion());
    }

    @Transactional
    public void submit(AppUser actor, AppUser subject, MonthlyTimesheet timesheet, Long expectedVersion) {
        authorisationService.requireTimesheetScope(actor, subject);
        boolean ownerOrReviewer = actor.getId().equals(subject.getId())
            || authorisationService.canReviewTimesheet(actor, subject);
        if (!StatusTransitionPolicy.canSubmit(timesheet.getStatus(), actor.getRole(), ownerOrReviewer)) {
            throw new SecurityException("Cannot submit timesheet");
        }

        timesheet.setStatus(TimesheetStatus.SUBMITTED);
        timesheet.setSubmittedAt(Instant.now());
        timesheet.setSubmittedByUserId(actor.getId());
        if (timesheetRepository.submitDraft(timesheet.getId(), timesheet.getSubmittedAt(), actor.getId(), expectedVersion) != 1) {
            throw new TimesheetConflictException("This timesheet was changed before it could be submitted. Reload and try again.");
        }
        auditService.record(
            actor.getId(),
            subject.getId(),
            AuditEventType.TIMESHEET_SUBMITTED.name(),
            "monthly_timesheet",
            timesheet.getId(),
            workflowDetails(timesheet, "DRAFT", "SUBMITTED", null)
        );
    }

    @Transactional
    public void approve(AppUser actor, AppUser subject, MonthlyTimesheet timesheet) {
        approve(actor, subject, timesheet, timesheet.getVersion());
    }

    @Transactional
    public void approve(AppUser actor, AppUser subject, MonthlyTimesheet timesheet, Long expectedVersion) {
        authorisationService.requireTimesheetReviewScope(actor, subject);
        if (!StatusTransitionPolicy.canApprove(timesheet.getStatus(), actor.getRole(), true)) {
            throw new SecurityException("Cannot approve timesheet");
        }

        timesheet.setStatus(TimesheetStatus.APPROVED);
        timesheet.setApprovedAt(Instant.now());
        timesheet.setApprovedByUserId(actor.getId());
        if (timesheetRepository.approveSubmitted(timesheet.getId(), timesheet.getApprovedAt(), actor.getId(), expectedVersion) != 1) {
            throw new TimesheetConflictException("This timesheet was changed before it could be approved. Reload and try again.");
        }
        auditService.record(
            actor.getId(),
            subject.getId(),
            AuditEventType.TIMESHEET_APPROVED.name(),
            "monthly_timesheet",
            timesheet.getId(),
            workflowDetails(timesheet, "SUBMITTED", "APPROVED", null)
        );
    }

    @Transactional
    public void reopen(AppUser actor, AppUser subject, MonthlyTimesheet timesheet, String reason) {
        reopen(actor, subject, timesheet, reason, timesheet.getVersion());
    }

    @Transactional
    public void reopen(AppUser actor, AppUser subject, MonthlyTimesheet timesheet, String reason, Long expectedVersion) {
        authorisationService.requireTimesheetReviewScope(actor, subject);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reopening reason is required.");
        }
        if (!StatusTransitionPolicy.canReopen(timesheet.getStatus(), actor.getRole(), true)) {
            throw new SecurityException("Cannot reopen timesheet");
        }

        TimesheetStatus previousStatus = timesheet.getStatus();
        Long previousSubmittedBy = timesheet.getSubmittedByUserId();
        Long previousApprovedBy = timesheet.getApprovedByUserId();
        Instant previousSubmittedAt = timesheet.getSubmittedAt();
        Instant previousApprovedAt = timesheet.getApprovedAt();

        timesheet.setStatus(TimesheetStatus.DRAFT);
        timesheet.setSubmittedAt(null);
        timesheet.setSubmittedByUserId(null);
        timesheet.setApprovedAt(null);
        timesheet.setApprovedByUserId(null);
        if (timesheetRepository.reopenToDraft(timesheet.getId(), previousStatus, expectedVersion) != 1) {
            throw new TimesheetConflictException("This timesheet was changed before it could be reopened. Reload and try again.");
        }
        auditService.record(
            actor.getId(),
            subject.getId(),
            AuditEventType.TIMESHEET_REOPENED.name(),
            "monthly_timesheet",
            timesheet.getId(),
            reopenDetails(
                previousStatus,
                reason,
                previousSubmittedAt,
                previousSubmittedBy,
                previousApprovedAt,
                previousApprovedBy
            )
        );
    }

    private List<PrivilegedChange> saveEntries(MonthlyTimesheet timesheet, SaveTimesheetCommand command) {
        validateCommandMatchesTimesheet(timesheet, command);
        validateExpectedVersion(timesheet, command);
        validateEntries(command);
        Map<LocalDate, DailyTimeEntry> existing = existingEntries(timesheet.getId());
        List<PrivilegedChange> changes = new ArrayList<>();

        for (DailyEntryCommand entryCommand : command.entries()) {
            validateWorkDate(command.year(), command.month(), entryCommand.workDate());
            OptionalInt parsedDuration = DurationFormat.parse(entryCommand.durationText());
            String note = normaliseNote(entryCommand.note());
            DailyTimeEntry entry = existing.get(entryCommand.workDate());
            if (parsedDuration.isEmpty() && note == null) {
                if (entry != null) {
                    changes.add(new PrivilegedChange(
                        entryCommand.workDate(),
                        entry.getDurationMinutes(),
                        null,
                        entry.getNote(),
                        null
                    ));
                    entryRepository.delete(entry);
                }
                continue;
            }

            Integer beforeDuration = entry == null ? null : entry.getDurationMinutes();
            String beforeNote = entry == null ? null : entry.getNote();
            int afterDuration = parsedDuration.orElse(0);

            if (entry == null) {
                entry = new DailyTimeEntry();
                entry.setTimesheetId(timesheet.getId());
                entry.setWorkDate(entryCommand.workDate());
                entry.setDurationMinutes(afterDuration);
                entry.setNote(note);
                entryRepository.save(entry);
            } else if (beforeDuration != afterDuration || !java.util.Objects.equals(beforeNote, note)) {
                entry.setDurationMinutes(afterDuration);
                entry.setNote(note);
                entryRepository.update(entry);
            }

            if (beforeDuration == null || beforeDuration != afterDuration || !java.util.Objects.equals(beforeNote, note)) {
                changes.add(new PrivilegedChange(entryCommand.workDate(), beforeDuration, afterDuration, beforeNote, note));
            }
        }

        return changes;
    }

    private Map<LocalDate, DailyTimeEntry> existingEntries(Long timesheetId) {
        Map<LocalDate, DailyTimeEntry> byDate = new HashMap<>();
        for (DailyTimeEntry entry : entryRepository.findByTimesheetId(timesheetId)) {
            byDate.put(entry.getWorkDate(), entry);
        }

        return byDate;
    }

    private MonthlyTimesheet createDraft(AppUser actor, AppUser subject, int year, int month) {
        MonthlyTimesheet timesheet = new MonthlyTimesheet();
        timesheet.setUserId(subject.getId());
        timesheet.setYear(year);
        timesheet.setMonth(month);
        timesheet.setStatus(TimesheetStatus.DRAFT);
        MonthlyTimesheet saved = timesheetRepository.save(timesheet);
        auditService.record(
            actor.getId(),
            subject.getId(),
            AuditEventType.TIMESHEET_CREATED.name(),
            "monthly_timesheet",
            saved.getId(),
            workflowDetails(saved, null, "DRAFT", null)
        );
        return saved;
    }

    private void validateCommandMatchesTimesheet(MonthlyTimesheet timesheet, SaveTimesheetCommand command) {
        validateMonth(command.year(), command.month());
        if (timesheet.getYear() != command.year() || timesheet.getMonth() != command.month()) {
            throw new IllegalArgumentException("Submitted month does not match the timesheet.");
        }
    }

    private void validateExpectedVersion(MonthlyTimesheet timesheet, SaveTimesheetCommand command) {
        if (command.expectedVersion() != null && !command.expectedVersion().equals(timesheet.getVersion())) {
            throw new TimesheetConflictException("This timesheet has changed since you opened it. Reload and try again.");
        }
    }

    private void validateMonth(int year, int month) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Year is outside the supported range.");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be from 1 to 12.");
        }
    }

    private void validateWorkDate(int year, int month, LocalDate workDate) {
        if (workDate == null || !YearMonth.of(year, month).equals(YearMonth.from(workDate))) {
            throw new IllegalArgumentException("Work date must belong to the selected month.");
        }
    }

    private void validateEntries(SaveTimesheetCommand command) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (DailyEntryCommand entryCommand : command.entries()) {
            String durationField = "duration_" + entryCommand.workDate();
            String noteField = "note_" + entryCommand.workDate();
            try {
                validateWorkDate(command.year(), command.month(), entryCommand.workDate());
            } catch (IllegalArgumentException exception) {
                errors.put(durationField, exception.getMessage());
                continue;
            }
            try {
                DurationFormat.parse(entryCommand.durationText());
            } catch (IllegalArgumentException exception) {
                errors.put(durationField, exception.getMessage());
            }
            if (entryCommand.note() != null && entryCommand.note().trim().length() > 1_000) {
                errors.put(noteField, "Notes must be 1,000 characters or fewer.");
            }
        }

        if (!errors.isEmpty()) {
            throw new TimesheetValidationException(command, errors);
        }
    }

    private String normaliseNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > 1_000) {
            throw new IllegalArgumentException("Notes must be 1,000 characters or fewer.");
        }

        return trimmed;
    }

    private String workflowDetails(MonthlyTimesheet timesheet, String previousStatus, String newStatus, String reason) {
        return "{"
            + jsonField("year", String.valueOf(timesheet.getYear())) + ","
            + jsonField("month", String.valueOf(timesheet.getMonth())) + ","
            + jsonField("previous_status", previousStatus) + ","
            + jsonField("new_status", newStatus) + ","
            + jsonField("reason", reason)
            + "}";
    }

    private String reopenDetails(
        TimesheetStatus previousStatus,
        String reason,
        Instant submittedAt,
        Long submittedBy,
        Instant approvedAt,
        Long approvedBy
    ) {
        return "{"
            + jsonField("previous_status", previousStatus.name()) + ","
            + jsonField("new_status", "DRAFT") + ","
            + jsonField("reason", reason.trim()) + ","
            + jsonField("previous_submitted_at", submittedAt == null ? null : submittedAt.toString()) + ","
            + jsonField("previous_submitted_by_user_id", submittedBy == null ? null : submittedBy.toString()) + ","
            + jsonField("previous_approved_at", approvedAt == null ? null : approvedAt.toString()) + ","
            + jsonField("previous_approved_by_user_id", approvedBy == null ? null : approvedBy.toString())
            + "}";
    }

    private String privilegedEditDetails(MonthlyTimesheet timesheet, List<PrivilegedChange> changes) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append(jsonField("year", String.valueOf(timesheet.getYear()))).append(',');
        json.append(jsonField("month", String.valueOf(timesheet.getMonth()))).append(',');
        json.append("\"changed_days\":[");
        for (int i = 0; i < changes.size(); i++) {
            PrivilegedChange change = changes.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append('{')
                .append(jsonField("work_date", change.workDate().toString())).append(',')
                .append(jsonField("duration_before", nullableInteger(change.durationBefore()))).append(',')
                .append(jsonField("duration_after", nullableInteger(change.durationAfter()))).append(',')
                .append(jsonField("note_before", change.noteBefore())).append(',')
                .append(jsonField("note_after", change.noteAfter()))
                .append('}');
        }
        json.append("]}");
        return json.toString();
    }

    private String nullableInteger(Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    private String jsonField(String name, String value) {
        return "\"" + escapeJson(name) + "\":" + (value == null ? "null" : "\"" + escapeJson(value) + "\"");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
