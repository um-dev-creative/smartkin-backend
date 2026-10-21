package com.umdc.smartkin.flow;

import com.umdc.smartkin.achievement.AchievementEvaluator;
import com.umdc.smartkin.achievement.AchievementTrigger;
import com.umdc.smartkin.api.v1.service.AssignmentServiceImpl;
import com.umdc.smartkin.api.v1.service.CatalogServiceImpl;
import com.umdc.smartkin.api.v1.service.ChildServiceImpl;
import com.umdc.smartkin.api.v1.service.NotificationServiceImpl;
import com.umdc.smartkin.api.v1.service.ParentServiceImpl;
import com.umdc.smartkin.api.v1.service.ProgressRecorder;
import com.umdc.smartkin.api.v1.service.ProgressServiceImpl;
import com.umdc.smartkin.api.v1.service.QuizAttemptServiceImpl;
import com.umdc.smartkin.api.v1.service.RewardsServiceImpl;
import com.umdc.smartkin.api.v1.to.AssignmentRequest;
import com.umdc.smartkin.api.v1.to.ChildRequest;
import com.umdc.smartkin.api.v1.to.ParentProfileRequest;
import com.umdc.smartkin.api.v1.to.ProgressRequest;
import com.umdc.smartkin.api.v1.to.QuizAnswerRequest;
import com.umdc.smartkin.api.v1.to.QuizAttemptRequest;
import com.umdc.smartkin.catalog.LessonCatalogService;
import com.umdc.smartkin.catalog.NoLessonCatalogCache;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.config.ClockConfig;
import com.umdc.smartkin.config.DataBaseConfig;
import com.umdc.smartkin.jpa.domain.ActivityEntity;
import com.umdc.smartkin.jpa.domain.TopicEntity;
import com.umdc.smartkin.jpa.repository.ActivityRepository;
import com.umdc.smartkin.jpa.repository.TopicRepository;
import com.umdc.smartkin.kafka.consumer.EventEffectsService;
import com.umdc.smartkin.kafka.consumer.KafkaConsumerConfig;
import com.umdc.smartkin.kafka.consumer.SmartKinEventListener;
import com.umdc.smartkin.kafka.event.DomainEventDispatcher;
import com.umdc.smartkin.kafka.event.SmartKinEventPublisher;
import com.umdc.smartkin.mapper.AssignmentMapperImpl;
import com.umdc.smartkin.mapper.CatalogMapperImpl;
import com.umdc.smartkin.mapper.ChildMapperImpl;
import com.umdc.smartkin.mapper.NotificationMapperImpl;
import com.umdc.smartkin.mapper.ParentMapperImpl;
import com.umdc.smartkin.mapper.QuizAttemptMapper;
import com.umdc.smartkin.mapper.RewardsMapperImpl;
import com.umdc.smartkin.mongo.document.LessonDocument;
import com.umdc.smartkin.mongo.document.LessonStatus;
import com.umdc.smartkin.mongo.repository.LessonRepository;
import com.umdc.smartkin.quiz.QuizGrader;
import com.umdc.smartkin.security.access.AccessPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.AutoConfigureDataJpa;
import org.springframework.boot.data.mongodb.test.autoconfigure.AutoConfigureDataMongo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static com.umdc.smartkin.support.TestData.tokenFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// BE-13: the whole MVP slice on **real infrastructure** — PostgreSQL (Flyway V1 to V5), MongoDB and Kafka — with the real services,
/// the real publisher and the real consumers, through the same service methods the controllers call:
/// a parent links a child and assigns an activity, the child gets a notification, reports progress, fails and then passes the quiz,
/// and the points, the achievement and its notification arrive asynchronously through Kafka.
///
/// What it does **not** start: the web layer, the security library, Vault, the TLS bundle and Redis (RD-1/RD-2 have their own tests);
/// booting the complete application needs the environment of BE-2.
@SpringBootTest(classes = EndToEndFlowContainerTest.App.class)
@Testcontainers
@AutoConfigureDataJpa
@AutoConfigureDataMongo
@ImportAutoConfiguration(classes = {
        org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
        org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration.class,
        org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.default-schema=smartkin",
        "spring.flyway.schemas=smartkin",
        "spring.flyway.create-schemas=true",
        "spring.flyway.url=",
        "spring.flyway.user=",
        "spring.flyway.password=",
        "spring.data.mongodb.auto-index-creation=true",
        "spring.mongodb.uri=mongodb://localhost/ignored",
        "prx.kafka.consumer.initial-interval=100ms",
        "prx.kafka.consumer.max-interval=200ms"
})
class EndToEndFlowContainerTest {

    private static final String[] TOPICS = {"smartkin.activity.assigned.v1", "smartkin.quiz.completed.v1", "smartkin.achievement.earned.v1",
            "smartkin.events.error.v1"};

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");
    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("prx.bootstrap.server.url", kafka::getBootstrapServers);
        registry.add("prx.kafka.consumer.group-id", () -> "e2e-" + UUID.randomUUID());
    }

    @BeforeAll
    static void createTopics() throws Exception {
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
            admin.createTopics(java.util.Arrays.stream(TOPICS).map(name -> new NewTopic(name, 1, (short) 1)).toList()).all().get();
        }
    }

    @SpringBootConfiguration
    @AutoConfigurationPackage(basePackages = "com.umdc.smartkin.mongo")
    @Import({DataBaseConfig.class, ClockConfig.class, AccessPolicy.class, ParentServiceImpl.class, ChildServiceImpl.class,
            CatalogServiceImpl.class, AssignmentServiceImpl.class, ProgressServiceImpl.class, QuizAttemptServiceImpl.class,
            RewardsServiceImpl.class, NotificationServiceImpl.class, ProgressRecorder.class, QuizGrader.class, QuizAttemptMapper.class,
            ParentMapperImpl.class, ChildMapperImpl.class, CatalogMapperImpl.class, AssignmentMapperImpl.class, RewardsMapperImpl.class,
            NotificationMapperImpl.class, LessonCatalogService.class, NoLessonCatalogCache.class, SmartKinEventPublisher.class,
            DomainEventDispatcher.class, EventEffectsService.class, SmartKinEventListener.class, KafkaConsumerConfig.class,
            AchievementEvaluator.class, AchievementTrigger.class})
    static class App {

        @Bean
        ObjectMapper mapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        KafkaTemplate<String, String> kafkaTemplate() {
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
        }
    }

    @Autowired
    private ParentServiceImpl parents;
    @Autowired
    private ChildServiceImpl children;
    @Autowired
    private CatalogServiceImpl catalog;
    @Autowired
    private AssignmentServiceImpl assignments;
    @Autowired
    private ProgressServiceImpl progress;
    @Autowired
    private QuizAttemptServiceImpl quiz;
    @Autowired
    private RewardsServiceImpl rewards;
    @Autowired
    private NotificationServiceImpl inbox;
    @Autowired
    private TopicRepository topics;
    @Autowired
    private ActivityRepository activities;
    @Autowired
    private LessonRepository lessons;
    @Autowired
    private MeterRegistry metrics;
    @MockitoBean
    private BackboneClient backboneClient;

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        var deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for " + what);
            }
            Thread.sleep(200);
        }
    }

    private UUID publishedLessonOf(UUID topicId) {
        var option1 = new LessonDocument.Option("o1", "Share it with a stranger", false);
        var option2 = new LessonDocument.Option("o2", "Tell an adult", true);
        var question = new LessonDocument.Question("q1", "s1", "SINGLE_CHOICE", "What do you do?", List.of(option1, option2), 10,
                "Tell a trusted adult");
        var scene = new LessonDocument.Scene("s1", 1, "Passwords", "Keep it secret", 12000L, List.of(), Map.of());
        var now = Instant.now();
        var lesson = new LessonDocument(UUID.randomUUID(), "passwords-" + UUID.randomUUID(), topicId, "Passwords", "Protect them", "en",
                LessonStatus.PUBLISHED, 1, List.of(scene), new LessonDocument.Quiz(70, 3, List.of(question)), UUID.randomUUID(), now, now);
        return lessons.save(lesson).id();
    }

    private UUID activityWithLesson(UUID topicId, UUID lessonId) {
        var activity = new ActivityEntity();
        activity.setTopic(topics.getReferenceById(topicId));
        activity.setTitle("Passwords " + UUID.randomUUID());
        activity.setDescription("Learn to protect them");
        activity.setContent("{}");
        activity.setDurationMinutes(10);
        activity.setPointsReward(50);
        activity.setCreatedAt(Instant.now());
        activity.setUpdatedAt(Instant.now());
        activity.setIsActive(true);
        activity.setLessonId(lessonId);
        return activities.save(activity).getId();
    }

    private static QuizAttemptRequest answer(String optionId) {
        return new QuizAttemptRequest(List.of(new QuizAnswerRequest("q1", List.of(optionId))));
    }

    @Test
    void assignThenFailThenPassTheQuizEndsWithPointsAnAchievementAndNotifications() throws Exception {
        var parent = UUID.randomUUID();
        var child = UUID.randomUUID();
        var otherParent = UUID.randomUUID();
        var parentToken = tokenFor(parent);
        var childToken = tokenFor(child);

        // Family setup
        assertEquals(HttpStatus.OK, parents.putMe(parentToken, new ParentProfileRequest("+15550100", "Elm St", null)).getStatusCode());
        assertEquals(HttpStatus.OK, parents.putMe(tokenFor(otherParent), new ParentProfileRequest(null, null, null)).getStatusCode());
        assertEquals(HttpStatus.CREATED, children.link(parentToken, new ChildRequest(child, "5", "fox.png")).getStatusCode());

        // Catalog: the parent browses and finds the published lesson
        var topic = new TopicEntity();
        topic.setName("Passwords " + UUID.randomUUID());
        var topicId = topics.save(topic).getId();
        var lessonId = publishedLessonOf(topicId);
        var activityId = activityWithLesson(topicId, lessonId);
        assertTrue(catalog.topics().getBody().stream().anyMatch(t -> t.topicId().equals(topicId)));
        assertEquals(List.of(lessonId), catalog.topicLessons(topicId).getBody().stream().map(l -> l.id()).toList());
        assertEquals(HttpStatus.OK, catalog.lesson(lessonId).getStatusCode());
        assertEquals(activityId, catalog.activity(activityId).getBody().activityId());

        // Assign: ACTIVITY_ASSIGNED travels through Kafka and becomes an inbox notification for the child
        var created = assignments.assign(parentToken, new AssignmentRequest(child, activityId, Instant.now().plus(Duration.ofDays(3))));
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        var assignmentId = created.getBody().assignmentId();
        await("the assignment notification", () -> inbox.unreadCount(childToken).getBody().count() == 1);
        var assigned = inbox.list(childToken, true, 0, 20).getBody().content().get(0);
        assertEquals("ASSIGNMENT", assigned.type().name());
        assertEquals(assignmentId, assigned.relatedEntityId());
        assertEquals(HttpStatus.NO_CONTENT, inbox.markRead(childToken, assigned.notificationId()).getStatusCode());
        assertEquals(0, inbox.unreadCount(childToken).getBody().count());
        assertEquals(0, inbox.unreadCount(parentToken).getBody().count());

        // The child works on it; the parent follows
        assertEquals(40, progress.update(childToken, assignmentId, new ProgressRequest(40)).getBody().completionPercentage());
        assertEquals(40, progress.get(parentToken, assignmentId).getBody().completionPercentage());

        // A failed attempt: stored, graded on the server, no completion, no points
        var failed = quiz.submit(childToken, assignmentId, answer("o1")).getBody();
        assertEquals(false, failed.passed());
        assertEquals(0, failed.score());
        assertEquals(1, failed.attemptNumber());
        assertEquals(0, progress.get(parentToken, assignmentId).getBody().score());
        assertNull(progress.get(parentToken, assignmentId).getBody().completionTime());

        // The passing attempt completes the assignment
        var passed = quiz.submit(childToken, assignmentId, answer("o2")).getBody();
        assertEquals(true, passed.passed());
        assertEquals(100, passed.percentage());
        assertEquals(2, passed.attemptNumber());
        var done = progress.get(parentToken, assignmentId).getBody();
        assertEquals(100, done.completionPercentage());
        assertEquals(100, done.score());
        assertTrue(done.completionTime() != null);

        // QUIZ_COMPLETED -> points; the check of the achievements -> ACHIEVEMENT_EARNED -> its points and its notification
        await("the quiz points, the achievement, its points and its notification", () ->
                rewards.points(childToken, child, 0, 20).getBody().totalPoints() == 20
                        && inbox.unreadCount(childToken).getBody().count() == 1);
        var points = rewards.points(parentToken, child, 0, 20).getBody();
        assertEquals(20, points.totalPoints());
        assertEquals(List.of("ACHIEVEMENT", "QUIZ"), points.transactions().content().stream().map(t -> t.sourceType()).sorted().toList());
        assertEquals(List.of("Primer quiz aprobado"), rewards.achievements(parentToken, child).getBody().stream().map(a -> a.name()).toList());
        assertEquals("ACHIEVEMENT", inbox.list(childToken, true, 0, 20).getBody().content().get(0).type().name());
        assertEquals(1, rewards.achievements(childToken, child).getBody().size());

        // Completing it frees the activity: the parent can assign it again, but not twice
        assertEquals(HttpStatus.CREATED, assignments.assign(parentToken, new AssignmentRequest(child, activityId, null)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, assignments.assign(parentToken, new AssignmentRequest(child, activityId, null)).getStatusCode());

        // Another family sees none of it
        var strangerToken = tokenFor(otherParent);
        assertEquals(HttpStatus.NOT_FOUND, rewards.points(strangerToken, child, 0, 20).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, progress.get(strangerToken, assignmentId).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, quiz.list(strangerToken, assignmentId).getStatusCode());
        assertEquals(2, quiz.list(parentToken, assignmentId).getBody().size());

        // Nothing went to the error topic along the way
        assertNull(metrics.find("smartkin.events.dead_lettered").counter());
        assertNull(metrics.find("smartkin.events.dead_letter_failed").counter());
    }
}
