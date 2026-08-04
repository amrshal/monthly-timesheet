package com.amrshalaby.timesheet.timesheet;

import com.amrshalaby.timesheet.audit.AuditService;
import com.amrshalaby.timesheet.user.UserRole;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;

@Singleton
public class TimesheetService {
    private final MonthlyTimesheetRepository timesheetRepository;
    private final DailyTimeEntryRepository entryRepository;
    private final AuditService auditService;

    public TimesheetService(
        MonthlyTimesheetRepository timesheetRepository,
        DailyTimeEntryRepository entryRepository,
        AuditService auditService
    ) {
        this.timesheetRepository = timesheetRepository;
        this.entryRepository = entryRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MonthlyTimesheet getOrCreate(Long userId, int year, int month) {
        return timesheetRepository.findByUserIdAndYearAndMonth(userId, year, month)
            .orElseGet(() -> createDraft(userId, year, month));
    }

    public List<DailyTimeEntry> entries(Long timesheetId) {
        return entryRepository.findByTimesheetId(timesheetId);
    }

    @Transactional
    public void submit(Long actorUserId, MonthlyTimesheet timesheet, UserRole role, boolean scoped) {
        if (!StatusTransitionPolicy.canSubmit(timesheet.getStatus(), role, scoped)) {
            throw new SecurityException("Cannot submit timesheet");
        }

        timesheet.setStatus(TimesheetStatus.SUBMITTED);
        timesheet.setSubmittedAt(Instant.now());
        timesheet.setSubmittedByUserId(actorUserId);
        timesheetRepository.update(timesheet);
        auditService.record(
            actorUserId,
            timesheet.getUserId(),
            "TIMESHEET_SUBMITTED",
            "monthly_timesheet",
            timesheet.getId(),
            "{}"
        );
    }

    @Transactional
    public void approve(Long actorUserId, MonthlyTimesheet timesheet, UserRole role, boolean scoped) {
        if (!StatusTransitionPolicy.canApprove(timesheet.getStatus(), role, scoped)) {
            throw new SecurityException("Cannot approve timesheet");
        }

        timesheet.setStatus(TimesheetStatus.APPROVED);
        timesheet.setApprovedAt(Instant.now());
        timesheet.setApprovedByUserId(actorUserId);
        timesheetRepository.update(timesheet);
        auditService.record(
            actorUserId,
            timesheet.getUserId(),
            "TIMESHEET_APPROVED",
            "monthly_timesheet",
            timesheet.getId(),
            "{}"
        );
    }

    @Transactional
    public void reopen(
        Long actorUserId,
        MonthlyTimesheet timesheet,
        UserRole role,
        boolean scoped,
        String reason
    ) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reopening reason is required.");
        }
        if (!StatusTransitionPolicy.canReopen(timesheet.getStatus(), role, scoped)) {
            throw new SecurityException("Cannot reopen timesheet");
        }

        TimesheetStatus previousStatus = timesheet.getStatus();
        timesheet.setStatus(TimesheetStatus.DRAFT);
        timesheet.setSubmittedAt(null);
        timesheet.setSubmittedByUserId(null);
        timesheet.setApprovedAt(null);
        timesheet.setApprovedByUserId(null);
        timesheetRepository.update(timesheet);
        auditService.record(
            actorUserId,
            timesheet.getUserId(),
            "TIMESHEET_REOPENED",
            "monthly_timesheet",
            timesheet.getId(),
            reopenDetails(previousStatus, reason)
        );
    }

    private MonthlyTimesheet createDraft(Long userId, int year, int month) {
        MonthlyTimesheet timesheet = new MonthlyTimesheet();
        timesheet.setUserId(userId);
        timesheet.setYear(year);
        timesheet.setMonth(month);
        timesheet.setStatus(TimesheetStatus.DRAFT);
        return timesheetRepository.save(timesheet);
    }

    private String reopenDetails(TimesheetStatus previousStatus, String reason) {
        return "{\"previous_status\":\"" + previousStatus
            + "\",\"new_status\":\"DRAFT\",\"reason\":\""
            + reason.replace("\"", "'")
            + "\"}";
    }
}
