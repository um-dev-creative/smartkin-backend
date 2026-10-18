package com.umdc.smartkin.mongo;

import com.umdc.smartkin.mongo.document.InteractionEventDocument;
import com.umdc.smartkin.mongo.document.LessonDocument;
import com.umdc.smartkin.mongo.document.LessonStatus;
import com.umdc.smartkin.mongo.document.QuizAttemptDocument;
import com.umdc.smartkin.mongo.repository.InteractionEventRepository;
import com.umdc.smartkin.mongo.repository.LessonRepository;
import com.umdc.smartkin.mongo.repository.QuizAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Round-trips the three SmartKin collections through a real MongoDB and checks the indexes the documents declare.
@DataMongoTest
@Testcontainers
@ContextConfiguration(classes = MongoCollectionsContainerTest.MongoTestApplication.class)
@TestPropertySource(properties = {
        "spring.data.mongodb.auto-index-creation=true",
        // bootstrap.yml builds the Atlas URI from SMARTKIN_MONGO_* placeholders; the container connection details take precedence.
        "spring.mongodb.uri=mongodb://localhost/ignored"
})
class MongoCollectionsContainerTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class MongoTestApplication {
    }

    @Autowired
    private MongoTemplate template;
    @Autowired
    private LessonRepository lessons;
    @Autowired
    private QuizAttemptRepository attempts;
    @Autowired
    private InteractionEventRepository events;

    private final UUID topic = UUID.randomUUID();
    private final UUID child = UUID.randomUUID();

    @BeforeEach
    void cleanCollections() {
        lessons.deleteAll();
        attempts.deleteAll();
        events.deleteAll();
    }

    private LessonDocument lesson(String slug, LessonStatus status) {
        var option = new LessonDocument.Option("a", "Take breaks", true);
        var question = new LessonDocument.Question("q1", "scene-1", "CHOICE", "What helps?", List.of(option), 10, "Well done");
        var interaction = new LessonDocument.Interaction("i1", "DRAG", "Drag the clock", Map.of("target", "clock"));
        var scene = new LessonDocument.Scene("scene-1", 1, "Intro", "Hello", 5000, List.of(interaction), Map.of("lottie", "lessons/intro.json"));
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return new LessonDocument(UUID.randomUUID(), slug, topic, "Screen time", "Balance", "es", status, 1,
                List.of(scene), new LessonDocument.Quiz(70, 3, List.of(question)), UUID.randomUUID(), now, now);
    }

    private QuizAttemptDocument attempt(UUID lessonId, int number, UUID assignment) {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var answer = new QuizAttemptDocument.Answer("q1", List.of("a"), true, 10, now);
        return new QuizAttemptDocument(UUID.randomUUID(), child, lessonId, 1, assignment, number, List.of(answer), 10, 10, 100,
                true, now, now);
    }

    @Test
    void storesALessonWithItsScenesAndQuiz() {
        var saved = lessons.save(lesson("screen-time", LessonStatus.PUBLISHED));

        var reloaded = lessons.findById(saved.id()).orElseThrow();

        assertEquals(saved, reloaded);
        assertEquals("Drag the clock", reloaded.scenes().getFirst().interactions().getFirst().prompt());
        assertEquals(70, reloaded.quiz().passingPercentage());
        assertTrue(reloaded.quiz().questions().getFirst().options().getFirst().correct());
        assertInstanceOf(UUID.class, template.getCollection("lessons").find().first().get("_id"));
    }

    @Test
    void findsLessonsBySlugAndByTopicAndStatus() {
        lessons.save(lesson("screen-time", LessonStatus.PUBLISHED));
        lessons.save(lesson("privacy", LessonStatus.DRAFT));

        assertEquals("screen-time", lessons.findBySlug("screen-time").orElseThrow().slug());
        assertEquals(1, lessons.findByTopicIdAndStatus(topic, LessonStatus.PUBLISHED).size());
        assertTrue(lessons.findBySlug("missing").isEmpty());
    }

    @Test
    void refusesTwoLessonsWithTheSameSlug() {
        lessons.save(lesson("screen-time", LessonStatus.PUBLISHED));

        assertThrows(DuplicateKeyException.class, () -> lessons.save(lesson("screen-time", LessonStatus.DRAFT)));
    }

    @Test
    void storesAttemptsAndCountsThemPerChildAndLesson() {
        var lessonId = UUID.randomUUID();
        var assignment = UUID.randomUUID();
        attempts.save(attempt(lessonId, 1, assignment));
        attempts.save(attempt(lessonId, 2, assignment));

        var latestFirst = attempts.findByChildIdAndLessonIdOrderByAttemptNumberDesc(child, lessonId);

        assertEquals(List.of(2, 1), latestFirst.stream().map(QuizAttemptDocument::attemptNumber).toList());
        assertEquals(2, attempts.countByChildIdAndLessonId(child, lessonId));
        assertEquals(2, attempts.findByAssignmentId(assignment).size());
    }

    @Test
    void refusesARepeatedAttemptNumber() {
        var lessonId = UUID.randomUUID();
        attempts.save(attempt(lessonId, 1, UUID.randomUUID()));

        assertThrows(DuplicateKeyException.class, () -> attempts.save(attempt(lessonId, 1, UUID.randomUUID())));
    }

    @Test
    void storesInteractionEventsInTimeOrder() {
        var lessonId = UUID.randomUUID();
        var attemptId = UUID.randomUUID();
        var first = Instant.parse("2026-10-18T10:00:00Z");
        events.save(new InteractionEventDocument(UUID.randomUUID(), child, lessonId, attemptId, "s1", "i1", "TAP", Map.of(), first.plusSeconds(5), first.plus(Duration.ofDays(90))));
        events.save(new InteractionEventDocument(UUID.randomUUID(), child, lessonId, null, "s1", "i1", "DROP", Map.of("x", 1), first, first.plus(Duration.ofDays(90))));

        var ordered = events.findByChildIdAndLessonIdOrderByOccurredAtAsc(child, lessonId);

        assertEquals(List.of("DROP", "TAP"), ordered.stream().map(InteractionEventDocument::eventType).toList());
        assertEquals(1, events.findByAttemptId(attemptId).size());
    }

    @Test
    void createsTheDeclaredIndexes() {
        lessons.count();
        Set<String> lessonIndexes = names("lessons");
        Set<String> attemptIndexes = names("quiz_attempts");
        Set<String> eventIndexes = names("interaction_events");

        assertTrue(lessonIndexes.containsAll(Set.of("slug", "topic_status")), lessonIndexes.toString());
        assertTrue(attemptIndexes.containsAll(Set.of("child_lesson_attempt", "child_completed", "assignmentId")), attemptIndexes.toString());
        assertTrue(eventIndexes.containsAll(Set.of("child_lesson_time", "attemptId", "expires_at_ttl")), eventIndexes.toString());
    }

    @Test
    void expiresInteractionEventsThroughATtlIndexOnExpiresAt() {
        var ttl = template.indexOps("interaction_events").getIndexInfo().stream()
                .filter(index -> index.getName().equals("expires_at_ttl")).findFirst().orElseThrow();

        assertEquals(Duration.ZERO, ttl.getExpireAfter().orElseThrow());
        assertEquals("expiresAt", ttl.getIndexFields().getFirst().getKey());
    }

    private Set<String> names(String collection) {
        return template.indexOps(collection).getIndexInfo().stream().map(IndexInfo::getName).collect(Collectors.toSet());
    }
}
