package com.amrshalaby.timesheet.timesheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amrshalaby.timesheet.audit.AuditEvent;
import com.amrshalaby.timesheet.audit.AuditEventRepository;
import com.amrshalaby.timesheet.audit.AuditService;
import com.amrshalaby.timesheet.user.AppUser;
import com.amrshalaby.timesheet.user.AuthorisationService;
import com.amrshalaby.timesheet.user.UserRole;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TimesheetServiceTest {
    @Test
    void blankDurationAndBlankNoteClearsExistingEntry() {
        InMemoryMonthlyTimesheetRepository timesheets = new InMemoryMonthlyTimesheetRepository();
        InMemoryDailyTimeEntryRepository entries = new InMemoryDailyTimeEntryRepository();
        TimesheetService service = service(timesheets, entries, new InMemoryAuditEventRepository());

        MonthlyTimesheet timesheet = timesheet(10L, 2026, 8, 4L);
        entries.save(entry(20L, 10L, LocalDate.of(2026, 8, 5), 480, "old note"));

        service.saveEmployeeDraft(
            user(1L, UserRole.EMPLOYEE, null),
            user(1L, UserRole.EMPLOYEE, null),
            timesheet,
            new SaveTimesheetCommand(
                2026,
                8,
                4L,
                List.of(new DailyEntryCommand(LocalDate.of(2026, 8, 5), "", ""))
            )
        );

        assertTrue(entries.findByTimesheetId(10L).isEmpty());
    }

    @Test
    void staleExpectedVersionIsRejected() {
        InMemoryMonthlyTimesheetRepository timesheets = new InMemoryMonthlyTimesheetRepository();
        InMemoryDailyTimeEntryRepository entries = new InMemoryDailyTimeEntryRepository();
        TimesheetService service = service(timesheets, entries, new InMemoryAuditEventRepository());

        MonthlyTimesheet timesheet = timesheet(10L, 2026, 8, 4L);

        assertThrows(
            TimesheetConflictException.class,
            () -> service.saveEmployeeDraft(
                user(1L, UserRole.EMPLOYEE, null),
                user(1L, UserRole.EMPLOYEE, null),
                timesheet,
                new SaveTimesheetCommand(
                    2026,
                    8,
                    3L,
                    List.of(new DailyEntryCommand(LocalDate.of(2026, 8, 5), "08:00", ""))
                )
            )
        );
        assertEquals(0, entries.findByTimesheetId(10L).size());
    }

    @Test
    void invalidEntryPreservesFieldErrorsAndDoesNotPartiallySave() {
        InMemoryMonthlyTimesheetRepository timesheets = new InMemoryMonthlyTimesheetRepository();
        InMemoryDailyTimeEntryRepository entries = new InMemoryDailyTimeEntryRepository();
        TimesheetService service = service(timesheets, entries, new InMemoryAuditEventRepository());

        MonthlyTimesheet timesheet = timesheet(10L, 2026, 8, 4L);
        TimesheetValidationException exception = assertThrows(
            TimesheetValidationException.class,
            () -> service.saveEmployeeDraft(
                user(1L, UserRole.EMPLOYEE, null),
                user(1L, UserRole.EMPLOYEE, null),
                timesheet,
                new SaveTimesheetCommand(
                    2026,
                    8,
                    4L,
                    List.of(
                        new DailyEntryCommand(LocalDate.of(2026, 8, 5), "08:00", ""),
                        new DailyEntryCommand(LocalDate.of(2026, 8, 6), "25:00", "")
                    )
                )
            )
        );

        assertEquals(
            "Enter a duration from 00:00 to 24:00.",
            exception.fieldErrors().get("duration_2026-08-06")
        );
        assertEquals(0, entries.findByTimesheetId(10L).size());
    }

    @Test
    void approvalUsesAtomicSubmittedStatusCheck() {
        InMemoryMonthlyTimesheetRepository timesheets = new InMemoryMonthlyTimesheetRepository();
        InMemoryDailyTimeEntryRepository entries = new InMemoryDailyTimeEntryRepository();
        TimesheetService service = service(timesheets, entries, new InMemoryAuditEventRepository());
        MonthlyTimesheet timesheet = timesheet(10L, 2026, 8, 4L);
        timesheet.setStatus(TimesheetStatus.SUBMITTED);
        timesheets.save(timesheet);
        timesheets.forceAtomicUpdateFailure();

        assertThrows(
            TimesheetConflictException.class,
            () -> service.approve(
                user(2L, UserRole.MANAGER, null),
                user(1L, UserRole.EMPLOYEE, 2L),
                timesheet
            )
        );
    }

    private TimesheetService service(
        MonthlyTimesheetRepository timesheets,
        DailyTimeEntryRepository entries,
        AuditEventRepository auditEvents
    ) {
        return new TimesheetService(
            timesheets,
            entries,
            new AuditService(auditEvents),
            new AuthorisationService(false)
        );
    }

    private AppUser user(Long id, UserRole role, Long managerId) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        user.setManagerId(managerId);
        user.setActive(true);
        return user;
    }

    private MonthlyTimesheet timesheet(Long id, int year, int month, Long version) {
        MonthlyTimesheet timesheet = new MonthlyTimesheet();
        timesheet.setId(id);
        timesheet.setUserId(1L);
        timesheet.setYear(year);
        timesheet.setMonth(month);
        timesheet.setStatus(TimesheetStatus.DRAFT);
        timesheet.setVersion(version);
        return timesheet;
    }

    private DailyTimeEntry entry(Long id, Long timesheetId, LocalDate workDate, int durationMinutes, String note) {
        DailyTimeEntry entry = new DailyTimeEntry();
        entry.setId(id);
        entry.setTimesheetId(timesheetId);
        entry.setWorkDate(workDate);
        entry.setDurationMinutes(durationMinutes);
        entry.setNote(note);
        return entry;
    }

    private static final class InMemoryMonthlyTimesheetRepository implements MonthlyTimesheetRepository {
        private final List<MonthlyTimesheet> timesheets = new ArrayList<>();

        @Override
        public Optional<MonthlyTimesheet> findByUserIdAndYearAndMonth(Long userId, int year, int month) {
            return timesheets.stream()
                .filter(timesheet -> timesheet.getUserId().equals(userId)
                    && timesheet.getYear() == year
                    && timesheet.getMonth() == month)
                .findFirst();
        }

        @Override
        public List<MonthlyTimesheet> findByStatus(TimesheetStatus status) {
            return timesheets.stream().filter(timesheet -> timesheet.getStatus() == status).toList();
        }

        private boolean atomicUpdateFailure;

        void forceAtomicUpdateFailure() {
            atomicUpdateFailure = true;
        }

        @Override
        public long submitDraft(Long id, Instant submittedAt, Long submittedByUserId) {
            return transition(id, TimesheetStatus.DRAFT, TimesheetStatus.SUBMITTED, timesheet -> {
                timesheet.setSubmittedAt(submittedAt);
                timesheet.setSubmittedByUserId(submittedByUserId);
            });
        }

        @Override
        public long approveSubmitted(Long id, Instant approvedAt, Long approvedByUserId) {
            return transition(id, TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED, timesheet -> {
                timesheet.setApprovedAt(approvedAt);
                timesheet.setApprovedByUserId(approvedByUserId);
            });
        }

        @Override
        public long reopenToDraft(Long id, TimesheetStatus previousStatus) {
            return transition(id, previousStatus, TimesheetStatus.DRAFT, timesheet -> {
                timesheet.setSubmittedAt(null);
                timesheet.setSubmittedByUserId(null);
                timesheet.setApprovedAt(null);
                timesheet.setApprovedByUserId(null);
            });
        }

        private long transition(
            Long id,
            TimesheetStatus expectedStatus,
            TimesheetStatus newStatus,
            java.util.function.Consumer<MonthlyTimesheet> mutator
        ) {
            if (atomicUpdateFailure) {
                return 0;
            }
            Optional<MonthlyTimesheet> timesheet = findById(id)
                .filter(candidate -> candidate.getStatus() == expectedStatus);
            timesheet.ifPresent(candidate -> {
                candidate.setStatus(newStatus);
                mutator.accept(candidate);
            });
            return timesheet.isPresent() ? 1 : 0;
        }

        @Override
        public <S extends MonthlyTimesheet> S save(S entity) {
            timesheets.add(entity);
            return entity;
        }

        @Override
        public <S extends MonthlyTimesheet> S insert(S entity) {
            return save(entity);
        }

        @Override
        public <S extends MonthlyTimesheet> S update(S entity) {
            return entity;
        }

        @Override
        public <S extends MonthlyTimesheet> List<S> updateAll(Iterable<S> entities) {
            return list(entities);
        }

        @Override
        public <S extends MonthlyTimesheet> List<S> insertAll(Iterable<S> entities) {
            return saveAll(entities);
        }

        @Override
        public <S extends MonthlyTimesheet> List<S> saveAll(Iterable<S> entities) {
            List<S> saved = list(entities);
            timesheets.addAll(saved);
            return saved;
        }

        @Override
        public Optional<MonthlyTimesheet> findById(Long id) {
            return timesheets.stream().filter(timesheet -> timesheet.getId().equals(id)).findFirst();
        }

        @Override
        public boolean existsById(Long id) {
            return findById(id).isPresent();
        }

        @Override
        public List<MonthlyTimesheet> findAll() {
            return timesheets;
        }

        @Override
        public long count() {
            return timesheets.size();
        }

        @Override
        public void deleteById(Long id) {
            timesheets.removeIf(timesheet -> timesheet.getId().equals(id));
        }

        @Override
        public void delete(MonthlyTimesheet entity) {
            timesheets.remove(entity);
        }

        @Override
        public void deleteAll(Iterable<? extends MonthlyTimesheet> entities) {
            for (MonthlyTimesheet entity : entities) {
                delete(entity);
            }
        }

        @Override
        public void deleteAll() {
            timesheets.clear();
        }
    }

    private static final class InMemoryDailyTimeEntryRepository implements DailyTimeEntryRepository {
        private final List<DailyTimeEntry> entries = new ArrayList<>();

        @Override
        public List<DailyTimeEntry> findByTimesheetId(Long timesheetId) {
            return entries.stream().filter(entry -> entry.getTimesheetId().equals(timesheetId)).toList();
        }

        @Override
        public Optional<DailyTimeEntry> findByTimesheetIdAndWorkDate(Long timesheetId, LocalDate workDate) {
            return entries.stream()
                .filter(entry -> entry.getTimesheetId().equals(timesheetId) && entry.getWorkDate().equals(workDate))
                .findFirst();
        }

        @Override
        public <S extends DailyTimeEntry> S save(S entity) {
            entries.add(entity);
            return entity;
        }

        @Override
        public <S extends DailyTimeEntry> S insert(S entity) {
            return save(entity);
        }

        @Override
        public <S extends DailyTimeEntry> S update(S entity) {
            return entity;
        }

        @Override
        public <S extends DailyTimeEntry> List<S> updateAll(Iterable<S> entities) {
            return list(entities);
        }

        @Override
        public <S extends DailyTimeEntry> List<S> insertAll(Iterable<S> entities) {
            return saveAll(entities);
        }

        @Override
        public <S extends DailyTimeEntry> List<S> saveAll(Iterable<S> entities) {
            List<S> saved = list(entities);
            entries.addAll(saved);
            return saved;
        }

        @Override
        public Optional<DailyTimeEntry> findById(Long id) {
            return entries.stream().filter(entry -> entry.getId().equals(id)).findFirst();
        }

        @Override
        public boolean existsById(Long id) {
            return findById(id).isPresent();
        }

        @Override
        public List<DailyTimeEntry> findAll() {
            return entries;
        }

        @Override
        public long count() {
            return entries.size();
        }

        @Override
        public void deleteById(Long id) {
            entries.removeIf(entry -> entry.getId().equals(id));
        }

        @Override
        public void delete(DailyTimeEntry entity) {
            entries.remove(entity);
        }

        @Override
        public void deleteAll(Iterable<? extends DailyTimeEntry> entities) {
            for (DailyTimeEntry entity : entities) {
                delete(entity);
            }
        }

        @Override
        public void deleteAll() {
            entries.clear();
        }
    }

    private static final class InMemoryAuditEventRepository implements AuditEventRepository {
        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public List<AuditEvent> findBySubjectUserId(Long subjectUserId) {
            return events.stream()
                .filter(event -> java.util.Objects.equals(event.getSubjectUserId(), subjectUserId))
                .toList();
        }

        @Override
        public <S extends AuditEvent> S save(S entity) {
            events.add(entity);
            return entity;
        }

        @Override
        public <S extends AuditEvent> S insert(S entity) {
            return save(entity);
        }

        @Override
        public <S extends AuditEvent> S update(S entity) {
            return entity;
        }

        @Override
        public <S extends AuditEvent> List<S> updateAll(Iterable<S> entities) {
            return list(entities);
        }

        @Override
        public <S extends AuditEvent> List<S> insertAll(Iterable<S> entities) {
            return saveAll(entities);
        }

        @Override
        public <S extends AuditEvent> List<S> saveAll(Iterable<S> entities) {
            List<S> saved = list(entities);
            events.addAll(saved);
            return saved;
        }

        @Override
        public Optional<AuditEvent> findById(Long id) {
            return events.stream().filter(event -> event.getId().equals(id)).findFirst();
        }

        @Override
        public boolean existsById(Long id) {
            return findById(id).isPresent();
        }

        @Override
        public List<AuditEvent> findAll() {
            return events;
        }

        @Override
        public long count() {
            return events.size();
        }

        @Override
        public void deleteById(Long id) {
            events.removeIf(event -> event.getId().equals(id));
        }

        @Override
        public void delete(AuditEvent entity) {
            events.remove(entity);
        }

        @Override
        public void deleteAll(Iterable<? extends AuditEvent> entities) {
            for (AuditEvent event : entities) {
                delete(event);
            }
        }

        @Override
        public void deleteAll() {
            events.clear();
        }
    }

    private static <S> List<S> list(Iterable<S> entities) {
        List<S> values = new ArrayList<>();
        Iterator<S> iterator = entities.iterator();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }
}
