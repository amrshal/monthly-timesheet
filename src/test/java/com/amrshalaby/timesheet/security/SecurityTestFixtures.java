package com.amrshalaby.timesheet.security;

import com.amrshalaby.timesheet.audit.AuditEvent;
import com.amrshalaby.timesheet.audit.AuditEventRepository;
import com.amrshalaby.timesheet.timesheet.DailyTimeEntry;
import com.amrshalaby.timesheet.timesheet.DailyTimeEntryRepository;
import com.amrshalaby.timesheet.timesheet.MonthlyTimesheet;
import com.amrshalaby.timesheet.timesheet.MonthlyTimesheetRepository;
import com.amrshalaby.timesheet.timesheet.TimesheetStatus;
import com.amrshalaby.timesheet.user.AppUser;
import com.amrshalaby.timesheet.user.UserRepository;
import com.amrshalaby.timesheet.user.UserRole;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.filters.AuthenticationFetcher;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Publisher;

@Factory
final class SecurityTestFixtures {
    static final String USER_HEADER = "X-Test-User";
    static final String ROLE_HEADER = "X-Test-Roles";

    @Singleton
    AuthenticationFetcher<HttpRequest<?>> testAuthenticationFetcher() {
        return request -> {
            String user = request.getHeaders().get(USER_HEADER);
            if (user == null || user.isBlank()) {
                return Publishers.empty();
            }
            List<String> roles = List.of(Optional.ofNullable(request.getHeaders().get(ROLE_HEADER)).orElse("EMPLOYEE").split(","));
            return Publishers.just(Authentication.build(user, roles));
        };
    }

    @Singleton
    @Replaces(UserRepository.class)
    TestUserRepository testUserRepository() {
        return new TestUserRepository();
    }

    @Singleton
    @Replaces(MonthlyTimesheetRepository.class)
    TestMonthlyTimesheetRepository testMonthlyTimesheetRepository() {
        return new TestMonthlyTimesheetRepository();
    }

    @Singleton
    @Replaces(DailyTimeEntryRepository.class)
    TestDailyTimeEntryRepository testDailyTimeEntryRepository() {
        return new TestDailyTimeEntryRepository();
    }

    @Singleton
    @Replaces(AuditEventRepository.class)
    TestAuditEventRepository testAuditEventRepository() {
        return new TestAuditEventRepository();
    }

    @Singleton
    TransactionOperations<Object> testTransactionOperations() {
        return new TransactionOperations<>() {
            @Override
            public Object getConnection() {
                return null;
            }

            @Override
            public boolean hasConnection() {
                return true;
            }

            @Override
            public Optional<TransactionStatus<Object>> findTransactionStatus() {
                return Optional.empty();
            }

            @Override
            public <R> R execute(TransactionDefinition definition, TransactionCallback<Object, R> callback) {
                try {
                    return callback.call(new TestTransactionStatus(definition));
                } catch (RuntimeException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }

            @Override
            public boolean managesTransaction(TransactionStatus<Object> transactionStatus) {
                return transactionStatus instanceof TestTransactionStatus;
            }
        };
    }

    static <S> List<S> list(Iterable<S> entities) {
        List<S> values = new ArrayList<>();
        Iterator<S> iterator = entities.iterator();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }
}

@Controller("/test-errors")
@Secured(SecurityRule.IS_AUTHENTICATED)
final class TestErrorController {
    @Get("/unexpected")
    String unexpected() {
        throw new IllegalStateException("internal password-like detail");
    }
}

final class TestTransactionStatus implements TransactionStatus<Object> {
    private final TransactionDefinition definition;
    private final AtomicBoolean rollbackOnly = new AtomicBoolean();

    TestTransactionStatus(TransactionDefinition definition) {
        this.definition = definition;
    }

    @Override
    public Object getTransaction() {
        return this;
    }

    @Override
    public io.micronaut.data.connection.ConnectionStatus<Object> getConnectionStatus() {
        return new io.micronaut.data.connection.ConnectionStatus<>() {
            @Override
            public boolean isNew() {
                return true;
            }

            @Override
            public Object getConnection() {
                return null;
            }

            @Override
            public io.micronaut.data.connection.ConnectionDefinition getDefinition() {
                return definition.getConnectionDefinition();
            }

            @Override
            public void registerSynchronization(io.micronaut.data.connection.ConnectionSynchronization synchronization) {
                // No-op for in-memory tests.
            }
        };
    }

    @Override
    public boolean isNewTransaction() {
        return true;
    }

    @Override
    public void setRollbackOnly() {
        rollbackOnly.set(true);
    }

    @Override
    public boolean isRollbackOnly() {
        return rollbackOnly.get();
    }

    @Override
    public boolean isCompleted() {
        return false;
    }

    @Override
    public TransactionDefinition getTransactionDefinition() {
        return definition;
    }
}

@Singleton
final class TestUserRepository implements UserRepository {
    private final List<AppUser> users = new ArrayList<>();
    private long nextId = 1;

    void reset(AppUser... seedUsers) {
        users.clear();
        nextId = 1;
        for (AppUser user : seedUsers) {
            save(user);
        }
    }

    AppUser user(Long id, String email, String name, UserRole role, Long managerId, boolean active) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setDisplayName(name);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setManagerId(managerId);
        user.setActive(active);
        user.setMustChangePassword(false);
        user.setVersion(1L);
        return user;
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        return users.stream().filter(user -> user.getEmail().equals(email)).findFirst();
    }

    @Override
    public long countByRole(UserRole role) {
        return users.stream().filter(user -> user.getRole() == role).count();
    }

    @Override
    public List<AppUser> findByManagerId(Long managerId) {
        return users.stream().filter(user -> java.util.Objects.equals(user.getManagerId(), managerId)).toList();
    }

    @Override
    public long countUsers() {
        return users.size();
    }

    @Override
    public <S extends AppUser> S save(S entity) {
        if (entity.getId() == null) {
            entity.setId(nextId++);
        } else {
            nextId = Math.max(nextId, entity.getId() + 1);
        }
        users.removeIf(user -> user.getId().equals(entity.getId()));
        users.add(entity);
        return entity;
    }

    @Override
    public <S extends AppUser> S insert(S entity) {
        return save(entity);
    }

    @Override
    public <S extends AppUser> S update(S entity) {
        return save(entity);
    }

    @Override
    public <S extends AppUser> List<S> updateAll(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public <S extends AppUser> List<S> insertAll(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public <S extends AppUser> List<S> saveAll(Iterable<S> entities) {
        List<S> saved = SecurityTestFixtures.list(entities);
        saved.forEach(this::save);
        return saved;
    }

    @Override
    public Optional<AppUser> findById(Long id) {
        return users.stream().filter(user -> user.getId().equals(id)).findFirst();
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id).isPresent();
    }

    @Override
    public List<AppUser> findAll() {
        return users.stream().sorted(Comparator.comparing(AppUser::getId)).toList();
    }

    @Override
    public long count() {
        return users.size();
    }

    @Override
    public void deleteById(Long id) {
        users.removeIf(user -> user.getId().equals(id));
    }

    @Override
    public void delete(AppUser entity) {
        users.removeIf(user -> user.getId().equals(entity.getId()));
    }

    @Override
    public void deleteAll(Iterable<? extends AppUser> entities) {
        for (AppUser entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAll() {
        users.clear();
    }
}

@Singleton
final class TestMonthlyTimesheetRepository implements MonthlyTimesheetRepository {
    private final List<MonthlyTimesheet> timesheets = new ArrayList<>();
    private long nextId = 1;

    void reset(MonthlyTimesheet... seedTimesheets) {
        timesheets.clear();
        nextId = 1;
        for (MonthlyTimesheet timesheet : seedTimesheets) {
            save(timesheet);
        }
    }

    MonthlyTimesheet timesheet(Long id, Long userId, int year, int month, TimesheetStatus status) {
        MonthlyTimesheet timesheet = new MonthlyTimesheet();
        timesheet.setId(id);
        timesheet.setUserId(userId);
        timesheet.setYear(year);
        timesheet.setMonth(month);
        timesheet.setStatus(status);
        timesheet.setVersion(1L);
        if (status == TimesheetStatus.SUBMITTED || status == TimesheetStatus.APPROVED) {
            timesheet.setSubmittedAt(Instant.parse("2026-08-05T10:00:00Z"));
            timesheet.setSubmittedByUserId(userId);
        }
        if (status == TimesheetStatus.APPROVED) {
            timesheet.setApprovedAt(Instant.parse("2026-08-06T10:00:00Z"));
            timesheet.setApprovedByUserId(2L);
        }
        return timesheet;
    }

    private MonthlyTimesheet copy(MonthlyTimesheet source) {
        MonthlyTimesheet copy = new MonthlyTimesheet();
        copy.setId(source.getId());
        copy.setUserId(source.getUserId());
        copy.setYear(source.getYear());
        copy.setMonth(source.getMonth());
        copy.setStatus(source.getStatus());
        copy.setSubmittedAt(source.getSubmittedAt());
        copy.setSubmittedByUserId(source.getSubmittedByUserId());
        copy.setApprovedAt(source.getApprovedAt());
        copy.setApprovedByUserId(source.getApprovedByUserId());
        copy.setVersion(source.getVersion());
        return copy;
    }

    @Override
    public Optional<MonthlyTimesheet> findByUserIdAndYearAndMonth(Long userId, int year, int month) {
        return timesheets.stream()
            .filter(timesheet -> timesheet.getUserId().equals(userId))
            .filter(timesheet -> timesheet.getYear() == year)
            .filter(timesheet -> timesheet.getMonth() == month)
            .map(this::copy)
            .findFirst();
    }

    @Override
    public List<MonthlyTimesheet> findByStatus(TimesheetStatus status) {
        return timesheets.stream().filter(timesheet -> timesheet.getStatus() == status).map(this::copy).toList();
    }

    @Override
    public List<MonthlyTimesheet> findSubmittedForUsers(List<Long> userIds) {
        return timesheets.stream()
            .filter(timesheet -> userIds.contains(timesheet.getUserId()))
            .filter(timesheet -> timesheet.getStatus() == TimesheetStatus.SUBMITTED)
            .sorted(Comparator.comparing(MonthlyTimesheet::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MonthlyTimesheet::getId))
            .map(this::copy)
            .toList();
    }

    @Override
    public List<MonthlyTimesheet> findManaged(
        List<Long> userIds,
        Long employeeId,
        Integer year,
        Integer month,
        TimesheetStatus status
    ) {
        return timesheets.stream()
            .filter(timesheet -> userIds.contains(timesheet.getUserId()))
            .filter(timesheet -> employeeId == null || employeeId.equals(timesheet.getUserId()))
            .filter(timesheet -> year == null || year == timesheet.getYear())
            .filter(timesheet -> month == null || month == timesheet.getMonth())
            .filter(timesheet -> status == null || status == timesheet.getStatus())
            .sorted(Comparator
                .comparing(MonthlyTimesheet::getYear)
                .thenComparing(MonthlyTimesheet::getMonth)
                .thenComparing(MonthlyTimesheet::getId)
                .reversed())
            .map(this::copy)
            .toList();
    }

    @Override
    public long submitDraft(Long id, Instant submittedAt, Long submittedByUserId, Long expectedVersion) {
        return transition(id, TimesheetStatus.DRAFT, expectedVersion, TimesheetStatus.SUBMITTED, timesheet -> {
            timesheet.setSubmittedAt(submittedAt);
            timesheet.setSubmittedByUserId(submittedByUserId);
        });
    }

    @Override
    public long approveSubmitted(Long id, Instant approvedAt, Long approvedByUserId, Long expectedVersion) {
        return transition(id, TimesheetStatus.SUBMITTED, expectedVersion, TimesheetStatus.APPROVED, timesheet -> {
            timesheet.setApprovedAt(approvedAt);
            timesheet.setApprovedByUserId(approvedByUserId);
        });
    }

    @Override
    public long reopenToDraft(Long id, TimesheetStatus previousStatus, Long expectedVersion) {
        return transition(id, previousStatus, expectedVersion, TimesheetStatus.DRAFT, timesheet -> {
            timesheet.setSubmittedAt(null);
            timesheet.setSubmittedByUserId(null);
            timesheet.setApprovedAt(null);
            timesheet.setApprovedByUserId(null);
        });
    }

    private long transition(
        Long id,
        TimesheetStatus expectedStatus,
        Long expectedVersion,
        TimesheetStatus newStatus,
        java.util.function.Consumer<MonthlyTimesheet> mutator
    ) {
        Optional<MonthlyTimesheet> match = timesheets.stream()
            .filter(timesheet -> timesheet.getId().equals(id))
            .filter(timesheet -> timesheet.getStatus() == expectedStatus)
            .filter(timesheet -> java.util.Objects.equals(timesheet.getVersion(), expectedVersion))
            .findFirst();
        match.ifPresent(timesheet -> {
            timesheet.setStatus(newStatus);
            mutator.accept(timesheet);
            timesheet.setVersion(timesheet.getVersion() + 1);
        });
        return match.isPresent() ? 1 : 0;
    }

    @Override
    public <S extends MonthlyTimesheet> S save(S entity) {
        if (entity.getId() == null) {
            entity.setId(nextId++);
        } else {
            nextId = Math.max(nextId, entity.getId() + 1);
        }
        if (entity.getVersion() == null) {
            entity.setVersion(1L);
        }
        timesheets.removeIf(timesheet -> timesheet.getId().equals(entity.getId()));
        timesheets.add(copy(entity));
        return entity;
    }

    @Override
    public <S extends MonthlyTimesheet> S insert(S entity) {
        return save(entity);
    }

    @Override
    public <S extends MonthlyTimesheet> S update(S entity) {
        return save(entity);
    }

    @Override
    public <S extends MonthlyTimesheet> List<S> updateAll(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public <S extends MonthlyTimesheet> List<S> insertAll(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public <S extends MonthlyTimesheet> List<S> saveAll(Iterable<S> entities) {
        List<S> saved = SecurityTestFixtures.list(entities);
        saved.forEach(this::save);
        return saved;
    }

    @Override
    public Optional<MonthlyTimesheet> findById(Long id) {
        return timesheets.stream().filter(timesheet -> timesheet.getId().equals(id)).map(this::copy).findFirst();
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id).isPresent();
    }

    @Override
    public List<MonthlyTimesheet> findAll() {
        return timesheets.stream().sorted(Comparator.comparing(MonthlyTimesheet::getId)).map(this::copy).toList();
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
        deleteById(entity.getId());
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

@Singleton
final class TestDailyTimeEntryRepository implements DailyTimeEntryRepository {
    private final List<DailyTimeEntry> entries = new ArrayList<>();
    private long nextId = 1;

    void reset(DailyTimeEntry... seedEntries) {
        entries.clear();
        nextId = 1;
        for (DailyTimeEntry entry : seedEntries) {
            save(entry);
        }
    }

    DailyTimeEntry entry(Long id, Long timesheetId, LocalDate workDate, int minutes, String note) {
        DailyTimeEntry entry = new DailyTimeEntry();
        entry.setId(id);
        entry.setTimesheetId(timesheetId);
        entry.setWorkDate(workDate);
        entry.setDurationMinutes(minutes);
        entry.setNote(note);
        entry.setVersion(1L);
        return entry;
    }

    @Override
    public List<DailyTimeEntry> findByTimesheetId(Long timesheetId) {
        return entries.stream().filter(entry -> entry.getTimesheetId().equals(timesheetId)).toList();
    }

    @Override
    public List<DailyTimeEntry> findByTimesheetIdIn(List<Long> timesheetIds) {
        return entries.stream().filter(entry -> timesheetIds.contains(entry.getTimesheetId())).toList();
    }

    @Override
    public Optional<DailyTimeEntry> findByTimesheetIdAndWorkDate(Long timesheetId, LocalDate workDate) {
        return entries.stream()
            .filter(entry -> entry.getTimesheetId().equals(timesheetId))
            .filter(entry -> entry.getWorkDate().equals(workDate))
            .findFirst();
    }

    @Override
    public <S extends DailyTimeEntry> S save(S entity) {
        if (entity.getId() == null) {
            entity.setId(nextId++);
        } else {
            nextId = Math.max(nextId, entity.getId() + 1);
        }
        if (entity.getVersion() == null) {
            entity.setVersion(1L);
        }
        entries.removeIf(entry -> entry.getId().equals(entity.getId()));
        entries.add(entity);
        return entity;
    }

    @Override
    public <S extends DailyTimeEntry> S insert(S entity) {
        return save(entity);
    }

    @Override
    public <S extends DailyTimeEntry> S update(S entity) {
        return save(entity);
    }

    @Override
    public <S extends DailyTimeEntry> List<S> updateAll(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public <S extends DailyTimeEntry> List<S> insertAll(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public <S extends DailyTimeEntry> List<S> saveAll(Iterable<S> entities) {
        List<S> saved = SecurityTestFixtures.list(entities);
        saved.forEach(this::save);
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
        return entries.stream().sorted(Comparator.comparing(DailyTimeEntry::getId)).toList();
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
        deleteById(entity.getId());
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

@Singleton
final class TestAuditEventRepository implements AuditEventRepository {
    private final List<AuditEvent> events = new ArrayList<>();
    private long nextId = 1;

    void reset() {
        events.clear();
        nextId = 1;
    }

    List<AuditEvent> events() {
        return events;
    }

    @Override
    public List<AuditEvent> findBySubjectUserId(Long subjectUserId) {
        return events.stream().filter(event -> java.util.Objects.equals(event.getSubjectUserId(), subjectUserId)).toList();
    }

    @Override
    public List<AuditEvent> search(
        Long actorUserId,
        Long subjectUserId,
        String eventType,
        Long timesheetId,
        Instant fromInclusive,
        Instant toExclusive
    ) {
        return events.stream()
            .filter(event -> actorUserId == null || actorUserId.equals(event.getActorUserId()))
            .filter(event -> subjectUserId == null || subjectUserId.equals(event.getSubjectUserId()))
            .filter(event -> eventType == null || eventType.isBlank() || eventType.equals(event.getEventType()))
            .filter(event -> timesheetId == null
                || ("monthly_timesheet".equals(event.getEntityType()) && timesheetId.equals(event.getEntityId())))
            .filter(event -> fromInclusive == null || !event.getEventTime().isBefore(fromInclusive))
            .filter(event -> toExclusive == null || event.getEventTime().isBefore(toExclusive))
            .sorted(Comparator.comparing(AuditEvent::getEventTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AuditEvent::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed())
            .limit(500)
            .toList();
    }

    @Override
    public <S extends AuditEvent> S save(S entity) {
        if (entity.getId() == null) {
            entity.setId(nextId++);
        } else {
            nextId = Math.max(nextId, entity.getId() + 1);
        }
        events.removeIf(event -> event.getId().equals(entity.getId()));
        events.add(entity);
        return entity;
    }

    @Override
    public <S extends AuditEvent> S insert(S entity) {
        return save(entity);
    }

    @Override
    public <S extends AuditEvent> S update(S entity) {
        return save(entity);
    }

    @Override
    public <S extends AuditEvent> List<S> updateAll(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public <S extends AuditEvent> List<S> insertAll(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public <S extends AuditEvent> List<S> saveAll(Iterable<S> entities) {
        List<S> saved = SecurityTestFixtures.list(entities);
        saved.forEach(this::save);
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
        return events.stream().sorted(Comparator.comparing(AuditEvent::getId)).toList();
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
        deleteById(entity.getId());
    }

    @Override
    public void deleteAll(Iterable<? extends AuditEvent> entities) {
        for (AuditEvent entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAll() {
        events.clear();
    }
}
