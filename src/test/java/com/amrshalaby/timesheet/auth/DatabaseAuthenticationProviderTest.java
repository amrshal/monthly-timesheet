package com.amrshalaby.timesheet.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amrshalaby.timesheet.audit.AuditEvent;
import com.amrshalaby.timesheet.audit.AuditEventRepository;
import com.amrshalaby.timesheet.audit.AuditEventType;
import com.amrshalaby.timesheet.audit.AuditService;
import com.amrshalaby.timesheet.user.AppUser;
import com.amrshalaby.timesheet.user.UserRole;
import com.amrshalaby.timesheet.user.UserService;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DatabaseAuthenticationProviderTest {
    @Test
    void disabledUserCannotAuthenticateAndFailureIsAudited() {
        PasswordHasher passwordHasher = new PasswordHasher();
        AppUser disabled = user("employee@example.com", passwordHasher.hash("correct-password"));
        disabled.setActive(false);
        InMemoryAuditEventRepository auditEvents = new InMemoryAuditEventRepository();
        DatabaseAuthenticationProvider provider = new DatabaseAuthenticationProvider(
            new StubUserService(Optional.of(disabled)),
            passwordHasher,
            new AuditService(auditEvents)
        );

        AuthenticationResponse response = provider.authenticate(
            HttpRequest.GET("/login"),
            authRequest("employee@example.com", "correct-password")
        );

        assertFalse(response.isAuthenticated());
        assertEquals(AuditEventType.LOGIN_FAILED.name(), auditEvents.events.getFirst().getEventType());
    }

    private AuthenticationRequest<String, String> authRequest(String email, String password) {
        return new AuthenticationRequest<>() {
            @Override
            public String getIdentity() {
                return email;
            }

            @Override
            public String getSecret() {
                return password;
            }
        };
    }

    private AppUser user(String email, String passwordHash) {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setEmail(email);
        user.setDisplayName("Employee");
        user.setPasswordHash(passwordHash);
        user.setRole(UserRole.EMPLOYEE);
        user.setActive(true);
        return user;
    }

    private static final class StubUserService extends UserService {
        private final Optional<AppUser> user;

        private StubUserService(Optional<AppUser> user) {
            super(null, null, null, null, 12);
            this.user = user;
        }

        @Override
        public Optional<AppUser> findActiveByEmail(String email) {
            return user.filter(candidate -> candidate.isActive() && candidate.getEmail().equals(email));
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
                .limit(500)
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
