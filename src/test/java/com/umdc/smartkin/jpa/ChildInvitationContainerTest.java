package com.umdc.smartkin.jpa;

import com.umdc.smartkin.api.v1.service.ChildInvitationServiceImpl;
import com.umdc.smartkin.api.v1.service.ChildLinker;
import com.umdc.smartkin.api.v1.to.AcceptInvitationRequest;
import com.umdc.smartkin.api.v1.to.InvitationRequest;
import com.umdc.smartkin.config.ClockConfig;
import com.umdc.smartkin.config.DataBaseConfig;
import com.umdc.smartkin.invitation.InvitationCode;
import com.umdc.smartkin.jpa.domain.ChildInvitationEntity;
import com.umdc.smartkin.jpa.domain.InvitationStatus;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.repository.ChildInvitationRepository;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.mapper.ChildMapperImpl;
import com.umdc.smartkin.security.access.AccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static com.umdc.smartkin.support.TestData.tokenFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// S-2 on a real PostgreSQL (migration V6 included): an invitation works once, only while it is pending and unexpired, and a failed accept leaves it usable.
/// The tests run outside a test transaction because they exercise commits and concurrent callers.
@DataJpaTest
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ChildInvitationContainerTest.InvitationTestApplication.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.default-schema=smartkin",
        "spring.flyway.schemas=smartkin",
        "spring.flyway.create-schemas=true",
        "spring.flyway.url=",
        "spring.flyway.user=",
        "spring.flyway.password="
})
class ChildInvitationContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({DataBaseConfig.class, ClockConfig.class, AccessPolicy.class, ChildInvitationServiceImpl.class, ChildLinker.class, ChildMapperImpl.class})
    static class InvitationTestApplication {
    }

    @Autowired
    private ChildInvitationServiceImpl invitations;
    @Autowired
    private ChildInvitationRepository invitationRepository;
    @Autowired
    private ParentRepository parentRepository;
    @Autowired
    private ChildRepository childRepository;

    private UUID newParent() {
        var parent = new ParentEntity();
        parent.setId(UUID.randomUUID());
        return parentRepository.save(parent).getId();
    }

    private String inviteFrom(UUID parentId) {
        var created = invitations.create(tokenFor(parentId), new InvitationRequest("5", "fox.png"));
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        return created.getBody().code();
    }

    private ResponseEntity<?> accept(UUID userId, String code) {
        return invitations.accept(tokenFor(userId), new AcceptInvitationRequest(code));
    }

    private static <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        var pool = Executors.newFixedThreadPool(tasks.size());
        var start = new CountDownLatch(1);
        try {
            var futures = tasks.stream().map(task -> pool.submit(() -> {
                start.await();
                return task.call();
            })).toList();
            start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void anInvitationLinksTheUserOnceAndOnlyOnce() {
        var parent = newParent();
        var code = inviteFrom(parent);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();

        var accepted = accept(first, code);

        assertEquals(HttpStatus.CREATED, accepted.getStatusCode());
        var child = childRepository.findById(first).orElseThrow();
        assertEquals(parent, child.getParent().getId());
        assertEquals("5", child.getGradeLevel());
        assertEquals("fox.png", child.getAvatar());
        assertEquals(HttpStatus.NOT_FOUND, accept(second, code).getStatusCode());
        assertTrue(childRepository.findById(second).isEmpty());
        var stored = invitationRepository.findByCodeHash(InvitationCode.hash(InvitationCode.normalize(code).orElseThrow())).orElseThrow();
        assertEquals(InvitationStatus.ACCEPTED, stored.getStatus());
        assertEquals(first, stored.getAcceptedBy());
    }

    @Test
    void theCodeIsNeverStoredOnlyItsHash() {
        var parent = newParent();
        var code = inviteFrom(parent);

        var stored = invitationRepository.findAllByParentIdOrderByCreatedAtDesc(parent);

        assertEquals(1, stored.size());
        assertEquals(64, stored.get(0).getCodeHash().length());
        assertTrue(!stored.get(0).getCodeHash().contains(InvitationCode.normalize(code).orElseThrow()));
    }

    @Test
    void manyUsersRacingForTheSameCodeProduceExactlyOneChild() throws Exception {
        var parent = newParent();
        var code = inviteFrom(parent);
        var users = java.util.stream.IntStream.range(0, 8).mapToObj(i -> UUID.randomUUID()).toList();

        var results = runTogether(users.stream().<Callable<HttpStatus>>map(user -> () -> HttpStatus.valueOf(accept(user, code).getStatusCode().value())).toList());

        assertEquals(1, results.stream().filter(HttpStatus.CREATED::equals).count());
        assertEquals(7, results.stream().filter(HttpStatus.NOT_FOUND::equals).count());
        assertEquals(1, users.stream().filter(user -> childRepository.existsById(user)).count());
    }

    @Test
    void aFailedAcceptLeavesTheOtherInvitationUsable() throws Exception {
        var parentA = newParent();
        var parentB = newParent();
        var codeA = inviteFrom(parentA);
        var codeB = inviteFrom(parentB);
        var user = UUID.randomUUID();

        var results = runTogether(List.<Callable<HttpStatus>>of(
                () -> HttpStatus.valueOf(accept(user, codeA).getStatusCode().value()),
                () -> HttpStatus.valueOf(accept(user, codeB).getStatusCode().value())));

        assertEquals(1, results.stream().filter(HttpStatus.CREATED::equals).count(), results.toString());
        assertEquals(1, results.stream().filter(HttpStatus.CONFLICT::equals).count(), results.toString());
        var statuses = new ArrayList<InvitationStatus>();
        for (var code : List.of(codeA, codeB)) {
            statuses.add(invitationRepository.findByCodeHash(InvitationCode.hash(InvitationCode.normalize(code).orElseThrow())).orElseThrow().getStatus());
        }
        assertTrue(statuses.contains(InvitationStatus.ACCEPTED) && statuses.contains(InvitationStatus.PENDING), statuses.toString());
    }

    @Test
    void anExpiredOrRevokedInvitationCannotBeAccepted() {
        var parent = newParent();
        var expiredCode = InvitationCode.generate();
        var expired = new ChildInvitationEntity();
        expired.setParent(parentRepository.getReferenceById(parent));
        expired.setCodeHash(InvitationCode.hash(expiredCode));
        expired.setStatus(InvitationStatus.PENDING);
        expired.setCreatedAt(Instant.now().minusSeconds(3600));
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        invitationRepository.save(expired);
        var revokedCode = inviteFrom(parent);
        var revokedId = invitationRepository.findAllByParentIdOrderByCreatedAtDesc(parent).stream()
                .filter(invitation -> invitation.getStatus() == InvitationStatus.PENDING && invitation.getExpiresAt().isAfter(Instant.now())).findFirst().orElseThrow().getId();
        assertEquals(HttpStatus.NO_CONTENT, invitations.revoke(tokenFor(parent), revokedId).getStatusCode());

        assertEquals(HttpStatus.NOT_FOUND, accept(UUID.randomUUID(), expiredCode).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, accept(UUID.randomUUID(), revokedCode).getStatusCode());
        var listed = invitations.list(tokenFor(parent)).getBody();
        assertTrue(listed.stream().anyMatch(item -> "EXPIRED".equals(item.status())));
        assertTrue(listed.stream().anyMatch(item -> "REVOKED".equals(item.status())));
    }

    @Test
    void aParentCannotRevokeAnotherParentsInvitationNorExceedTheLimit() {
        var parent = newParent();
        var other = newParent();
        inviteFrom(parent);
        var id = invitationRepository.findAllByParentIdOrderByCreatedAtDesc(parent).get(0).getId();

        assertEquals(HttpStatus.NOT_FOUND, invitations.revoke(tokenFor(other), id).getStatusCode());
        for (var i = 1; i < 10; i++) {
            inviteFrom(parent);
        }
        assertEquals(HttpStatus.CONFLICT, invitations.create(tokenFor(parent), new InvitationRequest(null, null)).getStatusCode());
    }
}
