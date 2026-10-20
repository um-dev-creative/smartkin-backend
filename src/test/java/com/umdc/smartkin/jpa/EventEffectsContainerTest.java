package com.umdc.smartkin.jpa;

import com.umdc.smartkin.config.DataBaseConfig;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.NotificationType;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.domain.PointEntity;
import com.umdc.smartkin.jpa.domain.TransactionType;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.NotificationRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.jpa.repository.PointRepository;
import com.umdc.smartkin.kafka.consumer.EventEffectsService;
import com.umdc.smartkin.kafka.event.EventType;
import com.umdc.smartkin.kafka.event.SmartKinEvent;
import com.umdc.smartkin.kafka.event.payload.AchievementEarnedPayload;
import com.umdc.smartkin.kafka.event.payload.ActivityAssignedPayload;
import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The event effects against a real PostgreSQL with the Flyway schema: redeliveries change nothing, and the unique indexes of V3
/// reject duplicates even if two consumers race past the existence check.
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = EventEffectsContainerTest.App.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.default-schema=smartkin",
        "spring.flyway.schemas=smartkin",
        "spring.flyway.create-schemas=true",
        "spring.flyway.url=",
        "spring.flyway.user=",
        "spring.flyway.password="
})
class EventEffectsContainerTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({DataBaseConfig.class, EventEffectsService.class})
    static class App {
    }

    @Autowired
    private EventEffectsService effects;
    @Autowired
    private ParentRepository parents;
    @Autowired
    private ChildRepository children;
    @Autowired
    private PointRepository points;
    @Autowired
    private NotificationRepository notifications;

    private ChildEntity newChild() {
        var parent = new ParentEntity();
        parent.setId(UUID.randomUUID());
        parent.setPhoneNumber("+15550100");
        parents.save(parent);
        var child = new ChildEntity();
        child.setId(UUID.randomUUID());
        child.setParent(parent);
        child.setTotalPoints(0);
        child.setGradeLevel("5");
        return children.saveAndFlush(child);
    }

    private SmartKinEvent<QuizCompletedPayload> quiz(UUID child, UUID attempt, int score) {
        return SmartKinEvent.of(EventType.QUIZ_COMPLETED, child,
                new QuizCompletedPayload(attempt, child, UUID.randomUUID(), UUID.randomUUID(), 1, score, 10, score * 10, true, NOW),
                Clock.systemUTC());
    }

    @Test
    void aRedeliveredQuizBooksThePointsOnce() {
        var child = newChild();
        var attempt = UUID.randomUUID();

        effects.onQuizCompleted(quiz(child.getId(), attempt, 8));
        effects.onQuizCompleted(quiz(child.getId(), attempt, 8));

        assertEquals(1, points.count());
        assertEquals(8, children.findById(child.getId()).orElseThrow().getTotalPoints());
    }

    @Test
    void anAchievementBooksPointsAndAnInboxEntryOnce() {
        var child = newChild();
        var earned = UUID.randomUUID();
        var event = SmartKinEvent.of(EventType.ACHIEVEMENT_EARNED, child.getId(),
                new AchievementEarnedPayload(earned, child.getId(), UUID.randomUUID(), "Explorer", 20, NOW), Clock.systemUTC());

        effects.onAchievementEarned(event);
        effects.onAchievementEarned(event);

        assertEquals(20, children.findById(child.getId()).orElseThrow().getTotalPoints());
        assertTrue(notifications.existsByUserIdAndTypeAndRelatedEntityId(child.getId(), NotificationType.ACHIEVEMENT, earned));
        assertEquals(1, notifications.count());
    }

    @Test
    void anAssignmentNotifiesTheChildOnce() {
        var child = UUID.randomUUID();
        var assignment = UUID.randomUUID();
        var event = SmartKinEvent.of(EventType.ACTIVITY_ASSIGNED, child,
                new ActivityAssignedPayload(assignment, child, UUID.randomUUID(), UUID.randomUUID(), "Lesson", NOW), Clock.systemUTC());

        effects.onActivityAssigned(event);
        effects.onActivityAssigned(event);

        assertEquals(1, notifications.count());
    }

    @Test
    void theUniqueIndexRejectsAPointsDuplicateThatSlippedPastTheCheck() {
        var child = newChild();
        var source = UUID.randomUUID();
        points.saveAndFlush(point(child, source));

        assertThrows(DataIntegrityViolationException.class, () -> points.saveAndFlush(point(child, source)));
    }

    private PointEntity point(ChildEntity child, UUID source) {
        var point = new PointEntity();
        point.setChild(child);
        point.setAmount(5);
        point.setSourceType("QUIZ");
        point.setSourceId(source);
        point.setTransactionType(TransactionType.EARN);
        point.setTransactionDate(NOW);
        return point;
    }
}
