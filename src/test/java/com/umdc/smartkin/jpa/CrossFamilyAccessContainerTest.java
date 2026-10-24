package com.umdc.smartkin.jpa;

import com.umdc.smartkin.achievement.AchievementTrigger;
import com.umdc.smartkin.api.v1.service.AssignmentServiceImpl;
import com.umdc.smartkin.api.v1.service.ChildInvitationServiceImpl;
import com.umdc.smartkin.api.v1.service.ChildLinker;
import com.umdc.smartkin.api.v1.service.ChildServiceImpl;
import com.umdc.smartkin.api.v1.service.NotificationServiceImpl;
import com.umdc.smartkin.api.v1.service.ParentServiceImpl;
import com.umdc.smartkin.api.v1.service.ProgressRecorder;
import com.umdc.smartkin.api.v1.service.ProgressServiceImpl;
import com.umdc.smartkin.api.v1.service.QuizAttemptServiceImpl;
import com.umdc.smartkin.api.v1.service.RewardsServiceImpl;
import com.umdc.smartkin.api.v1.to.AssignmentRequest;
import com.umdc.smartkin.api.v1.to.AcceptInvitationRequest;
import com.umdc.smartkin.api.v1.to.InvitationRequest;
import com.umdc.smartkin.api.v1.to.ChildUpdateRequest;
import com.umdc.smartkin.api.v1.to.FeedbackRequest;
import com.umdc.smartkin.api.v1.to.ParentProfileRequest;
import com.umdc.smartkin.api.v1.to.ProgressRequest;
import com.umdc.smartkin.api.v1.to.QuizAnswerRequest;
import com.umdc.smartkin.api.v1.to.QuizAttemptRequest;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.config.ClockConfig;
import com.umdc.smartkin.config.DataBaseConfig;
import com.umdc.smartkin.jpa.domain.ActivityEntity;
import com.umdc.smartkin.jpa.domain.TopicEntity;
import com.umdc.smartkin.jpa.repository.ActivityRepository;
import com.umdc.smartkin.jpa.repository.TopicRepository;
import com.umdc.smartkin.kafka.event.DomainEventDispatcher;
import com.umdc.smartkin.kafka.event.SmartKinEventPublisher;
import com.umdc.smartkin.mapper.AssignmentMapperImpl;
import com.umdc.smartkin.mapper.NotificationMapperImpl;
import com.umdc.smartkin.mapper.QuizAttemptMapper;
import com.umdc.smartkin.mapper.RewardsMapperImpl;
import com.umdc.smartkin.mongo.document.LessonStatus;
import com.umdc.smartkin.mongo.document.QuizAttemptDocument;
import com.umdc.smartkin.mongo.repository.LessonRepository;
import com.umdc.smartkin.mongo.repository.QuizAttemptRepository;
import com.umdc.smartkin.quiz.QuizGrader;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.mapper.ChildMapperImpl;
import com.umdc.smartkin.mapper.ParentMapperImpl;
import com.umdc.smartkin.security.access.AccessPolicy;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.tokenFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Two families on a real PostgreSQL: whatever one family does through the services never reaches the other.
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = CrossFamilyAccessContainerTest.AccessTestApplication.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.default-schema=smartkin",
        "spring.flyway.schemas=smartkin",
        "spring.flyway.create-schemas=true",
        "spring.flyway.url=",
        "spring.flyway.user=",
        "spring.flyway.password="
})
class CrossFamilyAccessContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({DataBaseConfig.class, ClockConfig.class, AccessPolicy.class, ChildServiceImpl.class, ChildInvitationServiceImpl.class, ChildLinker.class, ParentServiceImpl.class,
            AssignmentServiceImpl.class, ProgressServiceImpl.class, QuizAttemptServiceImpl.class, RewardsServiceImpl.class, RewardsMapperImpl.class, NotificationServiceImpl.class, NotificationMapperImpl.class, ProgressRecorder.class, QuizGrader.class, QuizAttemptMapper.class,
            DomainEventDispatcher.class, ChildMapperImpl.class, ParentMapperImpl.class, AssignmentMapperImpl.class})
    static class AccessTestApplication {
    }

    @Autowired
    private ChildServiceImpl children;
    @Autowired
    private ChildInvitationServiceImpl invitations;
    @Autowired
    private ParentServiceImpl parents;
    @Autowired
    private ParentRepository parentRepository;
    @Autowired
    private ChildRepository childRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private AssignmentServiceImpl assignments;
    @Autowired
    private ProgressServiceImpl progress;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @MockitoBean
    private BackboneClient backboneClient;
    @MockitoBean
    private SmartKinEventPublisher eventPublisher;
    @MockitoBean
    private AchievementTrigger achievementTrigger;
    @MockitoBean
    private LessonRepository lessonRepository;
    @MockitoBean
    private QuizAttemptRepository attemptRepository;
    @Autowired
    private QuizAttemptServiceImpl quiz;
    @Autowired
    private RewardsServiceImpl rewards;
    @Autowired
    private NotificationServiceImpl inbox;
    @Autowired
    private com.umdc.smartkin.jpa.repository.NotificationRepository notificationRepository;

    private UUID parentA;
    private UUID parentB;
    private UUID childA1;
    private UUID childA2;
    private UUID childB1;
    private final UUID stranger = UUID.randomUUID();

    private UUID newParent() {
        var parent = new ParentEntity();
        parent.setId(UUID.randomUUID());
        return parentRepository.save(parent).getId();
    }

    private UUID newChild(UUID parentId) {
        var child = new ChildEntity();
        child.setId(UUID.randomUUID());
        child.setParent(parentRepository.getReferenceById(parentId));
        child.setTotalPoints(0);
        child.setGradeLevel("3");
        return childRepository.save(child).getId();
    }

    @BeforeEach
    void families() {
        parentA = newParent();
        parentB = newParent();
        childA1 = newChild(parentA);
        childA2 = newChild(parentA);
        childB1 = newChild(parentB);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void aParentListsOnlyTheirOwnChildren() {
        var listedA = children.list(tokenFor(parentA)).getBody();
        var listedB = children.list(tokenFor(parentB)).getBody();

        assertEquals(List.of(childA1, childA2).stream().sorted().toList(), listedA.stream().map(c -> c.childId()).sorted().toList());
        assertEquals(List.of(childB1), listedB.stream().map(c -> c.childId()).toList());
    }

    @Test
    void aParentCannotReadAnotherFamilysChild() {
        assertEquals(HttpStatus.OK, children.get(tokenFor(parentA), childA1).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, children.get(tokenFor(parentA), childB1).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, children.get(tokenFor(parentB), childA1).getStatusCode());
    }

    @Test
    void aParentCannotChangeAnotherFamilysChild() {
        var attempt = children.update(tokenFor(parentA), childB1, new ChildUpdateRequest("hacked", null));
        entityManager.flush();
        entityManager.clear();

        assertEquals(HttpStatus.NOT_FOUND, attempt.getStatusCode());
        assertEquals("3", childRepository.findById(childB1).orElseThrow().getGradeLevel());
        assertEquals(HttpStatus.OK, children.update(tokenFor(parentB), childB1, new ChildUpdateRequest("4", null)).getStatusCode());
    }

    @Test
    void aChildSeesOnlyThemselvesAndChangesNobody() {
        assertEquals(HttpStatus.OK, children.get(tokenFor(childA1), childA1).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, children.get(tokenFor(childA1), childA2).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, children.get(tokenFor(childA1), childB1).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, children.update(tokenFor(childA1), childA1, new ChildUpdateRequest("9", null)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, children.list(tokenFor(childA1)).getStatusCode());
    }

    @Test
    void aUserWithoutProfileReachesNothing() {
        assertEquals(HttpStatus.FORBIDDEN, children.get(tokenFor(stranger), childA1).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, children.update(tokenFor(stranger), childA1, new ChildUpdateRequest("9", null)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, children.list(tokenFor(stranger)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, invitations.create(tokenFor(stranger), new InvitationRequest(null, null)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, invitations.list(tokenFor(stranger)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, parents.getMe(tokenFor(stranger)).getStatusCode());
    }

    @Test
    void aFamilysChildCannotJoinAnotherFamilyByAcceptingAnInvitation() {
        var code = invitations.create(tokenFor(parentB), new InvitationRequest(null, null)).getBody().code();
        var response = invitations.accept(tokenFor(childA1), new AcceptInvitationRequest(code));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(parentA, childRepository.findById(childA1).orElseThrow().getParent().getId());
    }

    @Test
    void aChildCannotBecomeAParentAndAParentCannotBecomeAChild() {
        assertEquals(HttpStatus.FORBIDDEN, parents.putMe(tokenFor(childA1), new ParentProfileRequest(null, null, null)).getStatusCode());
        var code = invitations.create(tokenFor(parentA), new InvitationRequest(null, null)).getBody().code();
        assertEquals(HttpStatus.CONFLICT, invitations.accept(tokenFor(parentB), new AcceptInvitationRequest(code)).getStatusCode());
    }

    private UUID newActivityWithLesson(UUID lessonId) {
        var id = newActivity();
        activityRepository.findById(id).orElseThrow().setLessonId(lessonId);
        return id;
    }

    private UUID newActivity() {
        var topic = new TopicEntity();
        topic.setName("Screen time " + UUID.randomUUID());
        topicRepository.save(topic);
        var activity = new ActivityEntity();
        activity.setTopic(topic);
        activity.setTitle("Passwords " + UUID.randomUUID());
        activity.setDescription("d");
        activity.setContent("{}");
        activity.setDurationMinutes(10);
        activity.setPointsReward(50);
        activity.setCreatedAt(java.time.Instant.now());
        activity.setUpdatedAt(java.time.Instant.now());
        activity.setIsActive(true);
        return activityRepository.save(activity).getId();
    }

    @Test
    void aParentAssignsOnlyToTheirOwnChildren() {
        var activity = newActivity();

        var own = assignments.assign(tokenFor(parentA), new AssignmentRequest(childA1, activity, null));
        var foreign = assignments.assign(tokenFor(parentA), new AssignmentRequest(childB1, activity, null));
        var byChild = assignments.assign(tokenFor(childA2), new AssignmentRequest(childA2, activity, null));
        var byStranger = assignments.assign(tokenFor(stranger), new AssignmentRequest(childA1, activity, null));
        var again = assignments.assign(tokenFor(parentA), new AssignmentRequest(childA1, activity, null));

        assertEquals(HttpStatus.CREATED, own.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, foreign.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, byChild.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, byStranger.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, again.getStatusCode());
    }

    @Test
    void everyoneSeesOnlyTheAssignmentsOfTheirOwnFamily() {
        var activity = newActivity();
        var ofA1 = assignments.assign(tokenFor(parentA), new AssignmentRequest(childA1, activity, null)).getBody().assignmentId();
        var ofB1 = assignments.assign(tokenFor(parentB), new AssignmentRequest(childB1, activity, null)).getBody().assignmentId();
        entityManager.flush();
        entityManager.clear();

        var listedA = assignments.list(tokenFor(parentA), null, 0, 20).getBody();
        var listedB = assignments.list(tokenFor(parentB), null, 0, 20).getBody();
        var listedChild = assignments.list(tokenFor(childA1), null, 0, 20).getBody();

        assertEquals(List.of(ofA1), listedA.content().stream().map(a -> a.assignmentId()).toList());
        assertEquals(List.of(ofB1), listedB.content().stream().map(a -> a.assignmentId()).toList());
        assertEquals(List.of(ofA1), listedChild.content().stream().map(a -> a.assignmentId()).toList());
        assertEquals(HttpStatus.NOT_FOUND, assignments.list(tokenFor(parentA), childB1, 0, 20).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assignments.list(tokenFor(childA2), childA1, 0, 20).getStatusCode());
        assertEquals(HttpStatus.OK, assignments.get(tokenFor(parentA), ofA1).getStatusCode());
        assertEquals(HttpStatus.OK, assignments.get(tokenFor(childA1), ofA1).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assignments.get(tokenFor(parentB), ofA1).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assignments.get(tokenFor(childA2), ofA1).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assignments.get(tokenFor(stranger), ofA1).getStatusCode());
    }

    @Test
    void progressBelongsToTheChildAndIsReadOnlyForEveryoneElse() {
        var activity = newActivity();
        var id = assignments.assign(tokenFor(parentA), new AssignmentRequest(childA1, activity, null)).getBody().assignmentId();
        entityManager.flush();
        entityManager.clear();

        assertEquals(HttpStatus.OK, progress.update(tokenFor(childA1), id, new ProgressRequest(40)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, progress.update(tokenFor(parentA), id, new ProgressRequest(90)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, progress.update(tokenFor(childA2), id, new ProgressRequest(90)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, progress.update(tokenFor(parentB), id, new ProgressRequest(90)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, progress.get(tokenFor(parentB), id).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, progress.get(tokenFor(childA2), id).getStatusCode());
        assertEquals(40, progress.get(tokenFor(parentA), id).getBody().completionPercentage());
        assertEquals(HttpStatus.BAD_REQUEST, progress.update(tokenFor(childA1), id, new ProgressRequest(10)).getStatusCode());
    }

    @Test
    void onlyTheAssigningParentLeavesFeedback() {
        var activity = newActivity();
        var id = assignments.assign(tokenFor(parentA), new AssignmentRequest(childA1, activity, null)).getBody().assignmentId();
        entityManager.flush();
        entityManager.clear();

        assertEquals(HttpStatus.NOT_FOUND, progress.feedback(tokenFor(parentB), id, new FeedbackRequest("hi")).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, progress.feedback(tokenFor(childA1), id, new FeedbackRequest("hi")).getStatusCode());
        var saved = progress.feedback(tokenFor(parentA), id, new FeedbackRequest("Nice start")).getBody();
        assertEquals("Nice start", saved.parentFeedback());
        assertEquals(0, saved.completionPercentage());
        assertEquals("Nice start", assignments.get(tokenFor(childA1), id).getBody().progress().parentFeedback());
    }

    @Test
    void completingAnAssignmentFreesTheActivityToBeAssignedAgain() {
        var activity = newActivity();
        var id = assignments.assign(tokenFor(parentA), new AssignmentRequest(childA1, activity, null)).getBody().assignmentId();
        assertEquals(HttpStatus.CONFLICT, assignments.assign(tokenFor(parentA), new AssignmentRequest(childA1, activity, null)).getStatusCode());

        var done = progress.update(tokenFor(childA1), id, new ProgressRequest(100)).getBody();
        entityManager.flush();
        entityManager.clear();

        assertEquals(100, done.completionPercentage());
        assertEquals(true, done.completionTime() != null && done.startTime() != null);
        assertEquals(HttpStatus.CREATED, assignments.assign(tokenFor(parentA), new AssignmentRequest(childA1, activity, null)).getStatusCode());
    }

    private static final com.umdc.smartkin.mongo.document.LessonDocument LESSON = com.umdc.smartkin.support.TestData.lesson(LessonStatus.PUBLISHED);

    private UUID assignmentWithQuiz() {
        org.mockito.Mockito.when(lessonRepository.findById(LESSON.id())).thenReturn(java.util.Optional.of(LESSON));
        var activity = newActivityWithLesson(LESSON.id());
        var id = assignments.assign(tokenFor(parentA), new AssignmentRequest(childA1, activity, null)).getBody().assignmentId();
        entityManager.flush();
        entityManager.clear();
        return id;
    }

    private static QuizAttemptRequest answer(String option) {
        return new QuizAttemptRequest(List.of(new QuizAnswerRequest("q1", List.of(option))));
    }

    @Test
    void onlyTheAssignedChildTakesTheQuiz() {
        var id = assignmentWithQuiz();

        assertEquals(HttpStatus.FORBIDDEN, quiz.submit(tokenFor(parentA), id, answer("o2")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, quiz.submit(tokenFor(childA2), id, answer("o2")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, quiz.submit(tokenFor(parentB), id, answer("o2")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, quiz.list(tokenFor(parentB), id).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, quiz.list(tokenFor(childA2), id).getStatusCode());
        org.mockito.Mockito.verify(attemptRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failingKeepsTheAssignmentOpenAndPassingCompletesItWithTheBestScore() {
        var id = assignmentWithQuiz();
        org.mockito.Mockito.when(attemptRepository.save(org.mockito.ArgumentMatchers.any(QuizAttemptDocument.class)))
                .thenAnswer(call -> call.getArgument(0));

        var failed = quiz.submit(tokenFor(childA1), id, answer("o1")).getBody();
        entityManager.flush();
        entityManager.clear();
        var afterFail = progress.get(tokenFor(parentA), id).getBody();
        var passed = quiz.submit(tokenFor(childA1), id, answer("o2")).getBody();
        entityManager.flush();
        entityManager.clear();
        var afterPass = progress.get(tokenFor(parentA), id).getBody();

        assertEquals(false, failed.passed());
        assertEquals(0, afterFail.score());
        assertEquals(0, afterFail.completionPercentage());
        assertEquals(null, afterFail.completionTime());
        assertEquals(true, passed.passed());
        assertEquals(100, afterPass.score());
        assertEquals(100, afterPass.completionPercentage());
        assertEquals(true, afterPass.completionTime() != null);
    }

    @Test
    void pointsAndAchievementsAreVisibleOnlyToTheChildAndTheirParent() {
        assertEquals(HttpStatus.OK, rewards.points(tokenFor(parentA), childA1, 0, 20).getStatusCode());
        assertEquals(HttpStatus.OK, rewards.points(tokenFor(childA1), childA1, 0, 20).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, rewards.points(tokenFor(parentB), childA1, 0, 20).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, rewards.points(tokenFor(childA2), childA1, 0, 20).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, rewards.points(tokenFor(stranger), childA1, 0, 20).getStatusCode());
        assertEquals(HttpStatus.OK, rewards.achievements(tokenFor(parentA), childA2).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, rewards.achievements(tokenFor(parentA), childB1).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, rewards.achievements(tokenFor(childB1), childA1).getStatusCode());
        assertEquals(0, rewards.points(tokenFor(parentA), childA1, 0, 20).getBody().totalPoints());
    }

    private UUID newNotification(UUID userId) {
        var notification = new com.umdc.smartkin.jpa.domain.NotificationEntity();
        notification.setUserId(userId);
        notification.setType(com.umdc.smartkin.jpa.domain.NotificationType.ASSIGNMENT);
        notification.setTitle("t");
        notification.setMessage("m");
        notification.setCreatedAt(java.time.Instant.now());
        return notificationRepository.save(notification).getId();
    }

    @Test
    void everyoneHasTheirOwnInbox() {
        var ofA1 = newNotification(childA1);
        var ofB1 = newNotification(childB1);
        entityManager.flush();
        entityManager.clear();

        assertEquals(List.of(ofA1), inbox.list(tokenFor(childA1), false, 0, 20).getBody().content().stream().map(n -> n.notificationId()).toList());
        assertEquals(1, inbox.unreadCount(tokenFor(childB1)).getBody().count());
        assertEquals(HttpStatus.NOT_FOUND, inbox.markRead(tokenFor(childB1), ofA1).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, inbox.markRead(tokenFor(parentA), ofA1).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, inbox.markRead(tokenFor(childA1), ofA1).getStatusCode());
        assertEquals(0, inbox.unreadCount(tokenFor(childA1)).getBody().count());
        assertEquals(1, inbox.unreadCount(tokenFor(childB1)).getBody().count());
        assertEquals(HttpStatus.NO_CONTENT, inbox.markAllRead(tokenFor(parentA)).getStatusCode());
        entityManager.flush();
        entityManager.clear();
        assertEquals(1, inbox.unreadCount(tokenFor(childB1)).getBody().count());
        assertEquals(false, notificationRepository.findById(ofB1).orElseThrow().getIsRead());
    }
}
