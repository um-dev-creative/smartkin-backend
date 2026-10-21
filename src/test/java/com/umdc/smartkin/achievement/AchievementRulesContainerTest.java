package com.umdc.smartkin.achievement;

import com.umdc.smartkin.config.ClockConfig;
import com.umdc.smartkin.config.DataBaseConfig;
import com.umdc.smartkin.jpa.domain.AchievementEntity;
import com.umdc.smartkin.jpa.domain.ActivityEntity;
import com.umdc.smartkin.jpa.domain.ActivityProgressEntity;
import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.domain.TopicEntity;
import com.umdc.smartkin.jpa.repository.AchievementRepository;
import com.umdc.smartkin.jpa.repository.ActivityProgressRepository;
import com.umdc.smartkin.jpa.repository.ActivityRepository;
import com.umdc.smartkin.jpa.repository.AssignedActivityRepository;
import com.umdc.smartkin.jpa.repository.ChildAchievementRepository;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.jpa.repository.TopicRepository;
import com.umdc.smartkin.kafka.event.DomainEventDispatcher;
import com.umdc.smartkin.kafka.event.SmartKinEventPublisher;
import com.umdc.smartkin.kafka.event.payload.AchievementEarnedPayload;
import com.umdc.smartkin.mongo.document.QuizAttemptDocument;
import com.umdc.smartkin.mongo.repository.QuizAttemptRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// The achievement rules on a real PostgreSQL with the Flyway schema (V5 seeds three achievements). The class has no test transaction on
/// purpose: the evaluator runs in its own `REQUIRES_NEW` transaction and must see committed data.
@DataJpaTest
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AchievementRulesContainerTest.App.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.default-schema=smartkin",
        "spring.flyway.schemas=smartkin",
        "spring.flyway.create-schemas=true",
        "spring.flyway.url=",
        "spring.flyway.user=",
        "spring.flyway.password="
})
class AchievementRulesContainerTest {

    private static final UUID FIRST_QUIZ = UUID.fromString("a1000000-0000-4000-8000-000000000001");
    private static final UUID EXPLORER = UUID.fromString("a1000000-0000-4000-8000-000000000002");
    private static final UUID HUNDRED_POINTS = UUID.fromString("a1000000-0000-4000-8000-000000000003");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({DataBaseConfig.class, ClockConfig.class, AchievementEvaluator.class, DomainEventDispatcher.class})
    static class App {
        @Bean
        ObjectMapper mapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    private AchievementEvaluator evaluator;
    @Autowired
    private AchievementRepository achievements;
    @Autowired
    private ChildAchievementRepository earnedRepository;
    @Autowired
    private ParentRepository parents;
    @Autowired
    private ChildRepository children;
    @Autowired
    private TopicRepository topics;
    @Autowired
    private ActivityRepository activities;
    @Autowired
    private AssignedActivityRepository assignments;
    @Autowired
    private ActivityProgressRepository progresses;
    @MockitoBean
    private SmartKinEventPublisher publisher;
    @MockitoBean
    private QuizAttemptRepository attempts;

    private final List<UUID> customAchievements = new ArrayList<>();

    @AfterEach
    void removeWhatTheTestCreated() {
        customAchievements.forEach(id -> {
            earnedRepository.findAll().stream().filter(e -> e.getAchievement().getId().equals(id)).forEach(earnedRepository::delete);
            achievements.deleteById(id);
        });
        reset(publisher, attempts);
    }

    private ChildEntity newChild(int points) {
        var parent = new ParentEntity();
        parent.setId(UUID.randomUUID());
        parents.save(parent);
        var child = new ChildEntity();
        child.setId(UUID.randomUUID());
        child.setParent(parent);
        child.setTotalPoints(points);
        return children.save(child);
    }

    private AchievementEntity customAchievement(String name, String requirements, int pointsValue) {
        var achievement = new AchievementEntity();
        achievement.setName(name);
        achievement.setDescription(name);
        achievement.setPointsValue(pointsValue);
        achievement.setRequirements(requirements);
        var saved = achievements.save(achievement);
        customAchievements.add(saved.getId());
        return saved;
    }

    private ActivityEntity completeNewActivity(ChildEntity child, Instant completedAt) {
        var topic = new TopicEntity();
        topic.setName("Topic " + UUID.randomUUID());
        topics.save(topic);
        var activity = new ActivityEntity();
        activity.setTopic(topic);
        activity.setTitle("Activity " + UUID.randomUUID());
        activity.setDescription("d");
        activity.setContent("{}");
        activity.setDurationMinutes(5);
        activity.setPointsReward(5);
        activity.setCreatedAt(Instant.now());
        activity.setUpdatedAt(Instant.now());
        activity.setIsActive(true);
        var saved = activities.save(activity);
        completeAgain(child, saved, completedAt);
        return saved;
    }

    private void completeAgain(ChildEntity child, ActivityEntity activity, Instant completedAt) {
        var assignment = new AssignedActivityEntity();
        assignment.setChild(child);
        assignment.setActivity(activity);
        assignment.setAssignedBy(child.getParent());
        assignment.setAssignedAt(Instant.now());
        assignments.save(assignment);
        var progress = new ActivityProgressEntity();
        progress.setAssignment(assignment);
        progress.setCompletionPercentage(100);
        progress.setCompletionTime(completedAt);
        progresses.save(progress);
    }

    private static QuizAttemptDocument passedAttempt(UUID childId, UUID lessonId, int number) {
        return new QuizAttemptDocument(UUID.randomUUID(), childId, lessonId, 1, UUID.randomUUID(), number, List.of(), 10, 10, 100, true,
                Instant.now(), Instant.now());
    }

    private List<UUID> earnedBy(UUID childId) {
        return earnedRepository.findAchievementIdsByChildId(childId);
    }

    @Test
    void v5SeedsTheThreeStarterAchievementsWithValidRules() {
        var seeded = achievements.findAllById(List.of(FIRST_QUIZ, EXPLORER, HUNDRED_POINTS));

        assertEquals(3, seeded.size());
        var mapper = JsonMapper.builder().build();
        assertEquals(List.of(AchievementRuleType.QUIZZES_PASSED, AchievementRuleType.ACTIVITIES_COMPLETED, AchievementRuleType.POINTS_REACHED),
                List.of(FIRST_QUIZ, EXPLORER, HUNDRED_POINTS).stream().map(id -> seeded.stream().filter(a -> a.getId().equals(id)).findFirst()
                        .map(a -> AchievementRule.parse(a.getRequirements(), mapper).orElseThrow().type()).orElseThrow()).toList());
    }

    @Test
    void passingAQuizEarnsTheFirstQuizAchievementOnceAndPublishesIt() {
        var child = newChild(0);
        when(attempts.findByChildIdAndPassedTrue(child.getId())).thenReturn(List.of(passedAttempt(child.getId(), UUID.randomUUID(), 1)));

        var first = evaluator.evaluate(child.getId());
        var second = evaluator.evaluate(child.getId());

        assertEquals(List.of(FIRST_QUIZ), first);
        assertEquals(List.of(), second);
        assertEquals(List.of(FIRST_QUIZ), earnedBy(child.getId()));
        var payload = ArgumentCaptor.forClass(AchievementEarnedPayload.class);
        verify(publisher).achievementEarned(payload.capture());
        assertEquals(child.getId(), payload.getValue().childId());
        assertEquals(FIRST_QUIZ, payload.getValue().achievementId());
        assertEquals("Primer quiz aprobado", payload.getValue().achievementName());
        assertEquals(10, payload.getValue().pointsValue());
    }

    @Test
    void reachingThePointsThresholdEarnsTheAchievement() {
        var below = newChild(99);
        var enough = newChild(100);

        assertEquals(List.of(), evaluator.evaluate(below.getId()));
        assertEquals(List.of(HUNDRED_POINTS), evaluator.evaluate(enough.getId()));
    }

    @Test
    void activitiesCountOnceEachAndOnlyWhenCompleted() {
        var child = newChild(0);
        var repeated = completeNewActivity(child, Instant.now());
        completeNewActivity(child, Instant.now());
        assertEquals(List.of(), evaluator.evaluate(child.getId()));

        completeAgain(child, repeated, Instant.now());
        assertEquals(List.of(), evaluator.evaluate(child.getId()), "completing the same activity again is not a new activity");

        completeNewActivity(child, Instant.now());
        assertEquals(List.of(EXPLORER), evaluator.evaluate(child.getId()));
    }

    @Test
    void anOpenAssignmentDoesNotCount() {
        var child = newChild(0);
        var first = completeNewActivity(child, Instant.now());
        completeNewActivity(child, Instant.now());
        var open = new AssignedActivityEntity();
        open.setChild(child);
        open.setActivity(first);
        open.setAssignedBy(child.getParent());
        open.setAssignedAt(Instant.now());
        assignments.save(open);
        var started = new ActivityProgressEntity();
        started.setAssignment(open);
        started.setCompletionPercentage(60);
        progresses.save(started);

        assertEquals(List.of(), evaluator.evaluate(child.getId()));
    }

    @Test
    void quizzesCountPerDistinctLesson() {
        var achievement = customAchievement("Two lessons", "{\"type\":\"QUIZZES_PASSED\",\"threshold\":2}", 5);
        var child = newChild(0);
        var lessonA = UUID.randomUUID();
        var lessonB = UUID.randomUUID();
        when(attempts.findByChildIdAndPassedTrue(child.getId())).thenReturn(List.of(passedAttempt(child.getId(), lessonA, 1),
                passedAttempt(child.getId(), lessonA, 2)));
        evaluator.evaluate(child.getId());
        assertTrue(!earnedBy(child.getId()).contains(achievement.getId()), "two passes of the same lesson are one quiz");

        when(attempts.findByChildIdAndPassedTrue(child.getId())).thenReturn(List.of(passedAttempt(child.getId(), lessonA, 1),
                passedAttempt(child.getId(), lessonB, 1)));
        var awarded = evaluator.evaluate(child.getId());

        assertTrue(awarded.contains(achievement.getId()));
    }

    @Test
    void anAchievementWithoutAValidRuleIsNeverAwardedAndDoesNotBreakTheOthers() {
        var broken = customAchievement("Free text", "complete one lesson", 5);
        var unknown = customAchievement("Unknown type", "{\"type\":\"STREAK_DAYS\",\"threshold\":3}", 5);
        var child = newChild(150);

        var awarded = evaluator.evaluate(child.getId());

        assertEquals(List.of(HUNDRED_POINTS), awarded);
        assertTrue(!earnedBy(child.getId()).contains(broken.getId()) && !earnedBy(child.getId()).contains(unknown.getId()));
    }

    @Test
    void anUnknownChildEarnsNothing() {
        assertEquals(List.of(), evaluator.evaluate(UUID.randomUUID()));

        verify(publisher, never()).achievementEarned(any());
    }

    @Test
    void theUniqueIndexMakesTheAwardHappenOncePerChild() {
        var child = newChild(0);

        var first = earnedRepository.insertIfAbsent(UUID.randomUUID(), child.getId(), HUNDRED_POINTS, Instant.now());
        var again = earnedRepository.insertIfAbsent(UUID.randomUUID(), child.getId(), HUNDRED_POINTS, Instant.now());

        assertEquals(1, first);
        assertEquals(0, again);
        assertEquals(List.of(HUNDRED_POINTS), earnedBy(child.getId()));
    }
}
