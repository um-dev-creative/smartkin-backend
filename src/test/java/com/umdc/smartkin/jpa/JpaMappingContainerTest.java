package com.umdc.smartkin.jpa;

import com.umdc.smartkin.config.DataBaseConfig;
import com.umdc.smartkin.jpa.domain.AchievementEntity;
import com.umdc.smartkin.jpa.domain.ActivityEntity;
import com.umdc.smartkin.jpa.domain.ActivityProgressEntity;
import com.umdc.smartkin.jpa.domain.AssignedActivityEntity;
import com.umdc.smartkin.jpa.domain.ChildAchievementEntity;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ChildRewardEntity;
import com.umdc.smartkin.jpa.domain.GoalEntity;
import com.umdc.smartkin.jpa.domain.GoalStatus;
import com.umdc.smartkin.jpa.domain.NotificationEntity;
import com.umdc.smartkin.jpa.domain.NotificationType;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.domain.PointEntity;
import com.umdc.smartkin.jpa.domain.RewardEntity;
import com.umdc.smartkin.jpa.domain.RewardStatus;
import com.umdc.smartkin.jpa.domain.TopicEntity;
import com.umdc.smartkin.jpa.domain.TransactionType;
import com.umdc.smartkin.jpa.repository.AchievementRepository;
import com.umdc.smartkin.jpa.repository.ActivityProgressRepository;
import com.umdc.smartkin.jpa.repository.ActivityRepository;
import com.umdc.smartkin.jpa.repository.AssignedActivityRepository;
import com.umdc.smartkin.jpa.repository.ChildAchievementRepository;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ChildRewardRepository;
import com.umdc.smartkin.jpa.repository.GoalRepository;
import com.umdc.smartkin.jpa.repository.NotificationRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.jpa.repository.PointRepository;
import com.umdc.smartkin.jpa.repository.RewardRepository;
import com.umdc.smartkin.jpa.repository.TopicRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Applies the Flyway baseline to a real PostgreSQL and round-trips the whole domain model through the repositories.
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = JpaMappingContainerTest.JpaTestApplication.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.default-schema=smartkin",
        "spring.flyway.schemas=smartkin",
        "spring.flyway.create-schemas=true",
        // bootstrap.yml points Flyway at the Supabase direct connection; here it must use the container's datasource.
        "spring.flyway.url=",
        "spring.flyway.user=",
        "spring.flyway.password="
})
class JpaMappingContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(DataBaseConfig.class)
    static class JpaTestApplication {
    }

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TopicRepository topics;
    @Autowired
    private ActivityRepository activities;
    @Autowired
    private ParentRepository parents;
    @Autowired
    private ChildRepository children;
    @Autowired
    private AssignedActivityRepository assignments;
    @Autowired
    private ActivityProgressRepository progresses;
    @Autowired
    private AchievementRepository achievements;
    @Autowired
    private ChildAchievementRepository childAchievements;
    @Autowired
    private RewardRepository rewards;
    @Autowired
    private ChildRewardRepository childRewards;
    @Autowired
    private GoalRepository goals;
    @Autowired
    private PointRepository points;
    @Autowired
    private NotificationRepository notifications;

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private ParentEntity newParent() {
        var parent = new ParentEntity();
        parent.setId(UUID.randomUUID());
        parent.setPhoneNumber("+15550100");
        return parents.save(parent);
    }

    private ChildEntity newChild(ParentEntity parent) {
        var child = new ChildEntity();
        child.setId(UUID.randomUUID());
        child.setParent(parent);
        child.setTotalPoints(0);
        child.setGradeLevel("5");
        return children.save(child);
    }

    private ActivityEntity newActivity() {
        var topic = new TopicEntity();
        topic.setName("Screen time");
        topic.setAgeRangeMin(8);
        topic.setAgeRangeMax(14);
        topics.save(topic);
        var activity = new ActivityEntity();
        activity.setTopic(topic);
        activity.setTitle("Balance your screen time");
        activity.setDescription("An animated lesson");
        activity.setContent("{}");
        activity.setDurationMinutes(10);
        activity.setPointsReward(50);
        activity.setCreatedAt(Instant.now());
        activity.setUpdatedAt(Instant.now());
        activity.setIsActive(true);
        return activities.save(activity);
    }

    @Test
    void flywayCreatesTheThirteenTablesAndTheFourEnums() {
        var tables = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'smartkin' and table_type = 'BASE TABLE' and table_name <> 'flyway_schema_history'""",
                Integer.class);
        var enums = jdbc.queryForObject("""
                select count(*) from pg_type t join pg_namespace n on n.oid = t.typnamespace
                where n.nspname = 'smartkin' and t.typtype = 'e'""", Integer.class);

        assertEquals(13, tables);
        assertEquals(4, enums);
    }

    @Test
    void registersTheThirteenEntitiesOfTheSmartkinSchema() {
        Set<String> names = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getName).collect(Collectors.toSet());

        assertEquals(Set.of("AchievementEntity", "ActivityEntity", "ActivityProgressEntity", "AssignedActivityEntity",
                "ChildAchievementEntity", "ChildEntity", "ChildRewardEntity", "GoalEntity", "NotificationEntity",
                "ParentEntity", "PointEntity", "RewardEntity", "TopicEntity"), names);
    }

    @Test
    void persistsAndReloadsTheLearningGraph() {
        var parent = newParent();
        var child = newChild(parent);
        var activity = newActivity();
        var assignment = new AssignedActivityEntity();
        assignment.setChild(child);
        assignment.setActivity(activity);
        assignment.setAssignedBy(parent);
        assignment.setAssignedAt(Instant.now());
        assignments.save(assignment);
        var progress = new ActivityProgressEntity();
        progress.setAssignment(assignment);
        progress.setCompletionPercentage(100);
        progress.setScore(9);
        progress.setParentFeedback("Great job");
        progresses.save(progress);
        flushAndClear();

        var reloaded = progresses.findById(progress.getId()).orElseThrow();

        assertEquals(9, reloaded.getScore());
        assertEquals("Great job", reloaded.getParentFeedback());
        assertEquals(child.getId(), reloaded.getAssignment().getChild().getId());
        assertEquals("Balance your screen time", reloaded.getAssignment().getActivity().getTitle());
        assertEquals(parent.getId(), children.findById(child.getId()).orElseThrow().getParent().getId());
    }

    @Test
    void persistsAchievementsRewardsAndGoalsWithTheirEnums() {
        var parent = newParent();
        var child = newChild(parent);
        var achievement = new AchievementEntity();
        achievement.setName("First lesson");
        achievement.setDescription("Complete a lesson");
        achievement.setPointsValue(10);
        achievement.setRequirements("one lesson");
        achievements.save(achievement);
        var earned = new ChildAchievementEntity();
        earned.setChild(child);
        earned.setAchievement(achievement);
        earned.setEarnedAt(Instant.now());
        childAchievements.save(earned);
        var reward = new RewardEntity();
        reward.setName("Extra game time");
        reward.setDescription("30 minutes");
        reward.setPointsCost(100);
        reward.setIsDigital(false);
        rewards.save(reward);
        var redeemed = new ChildRewardEntity();
        redeemed.setChild(child);
        redeemed.setReward(reward);
        redeemed.setRedeemedAt(Instant.now());
        redeemed.setStatus(RewardStatus.APPROVED);
        childRewards.save(redeemed);
        var goal = new GoalEntity();
        goal.setChild(child);
        goal.setTitle("Finish 5 lessons");
        goal.setCreatedBy(parent);
        goal.setTargetValue(5);
        goal.setCurrentValue(1);
        goal.setUnitOfMeasure("lessons");
        goal.setStartDate(LocalDate.of(2026, 10, 1));
        goal.setTargetDate(LocalDate.of(2026, 10, 31));
        goal.setReward(reward);
        goal.setStatus(GoalStatus.ACTIVE);
        goals.save(goal);
        flushAndClear();

        assertEquals(RewardStatus.APPROVED, childRewards.findById(redeemed.getId()).orElseThrow().getStatus());
        var reloadedGoal = goals.findById(goal.getId()).orElseThrow();
        assertEquals(GoalStatus.ACTIVE, reloadedGoal.getStatus());
        assertEquals(reward.getId(), reloadedGoal.getReward().getId());
        assertEquals("First lesson", childAchievements.findById(earned.getId()).orElseThrow().getAchievement().getName());
    }

    @Test
    void persistsPointsAndNotificationsWithoutAUserForeignKey() {
        var child = newChild(newParent());
        var point = new PointEntity();
        point.setChild(child);
        point.setAmount(50);
        point.setSourceType("ACTIVITY");
        point.setSourceId(UUID.randomUUID());
        point.setTransactionDate(Instant.now());
        point.setTransactionType(TransactionType.EARN);
        points.save(point);
        var recipient = UUID.randomUUID();
        var notification = new NotificationEntity();
        notification.setUserId(recipient);
        notification.setTitle("New activity");
        notification.setMessage("Your parent assigned an activity");
        notification.setCreatedAt(Instant.now());
        notification.setIsRead(false);
        notification.setType(NotificationType.ASSIGNMENT);
        notifications.save(notification);
        flushAndClear();

        assertEquals(TransactionType.EARN, points.findById(point.getId()).orElseThrow().getTransactionType());
        var reloaded = notifications.findById(notification.getId()).orElseThrow();
        assertEquals(recipient, reloaded.getUserId());
        assertEquals(NotificationType.ASSIGNMENT, reloaded.getType());
        assertNotNull(reloaded.getCreatedAt());
    }

    @Test
    void deletingAParentCascadesToItsChildrenInTheDatabase() {
        var parent = newParent();
        var child = newChild(parent);
        flushAndClear();

        jdbc.update("delete from smartkin.parent where parent_id = ?", parent.getId());

        assertTrue(jdbc.queryForList("select 1 from smartkin.child where child_id = ?", child.getId()).isEmpty());
    }

    @Test
    void findsTheChildrenOfAParentWithoutMixingFamilies() {
        var parent = newParent();
        var other = newParent();
        var first = newChild(parent);
        var second = newChild(parent);
        newChild(other);
        flushAndClear();

        var found = children.findAllByParentId(parent.getId());

        assertEquals(2, found.size());
        assertTrue(found.stream().map(ChildEntity::getId).toList().containsAll(java.util.List.of(first.getId(), second.getId())));
        assertTrue(children.findAllByParentId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void v4AddsANullableLessonIdToActivities() {
        var column = jdbc.queryForMap("""
                select data_type, is_nullable from information_schema.columns
                where table_schema = 'smartkin' and table_name = 'activities' and column_name = 'lesson_id'""");

        assertEquals("uuid", column.get("data_type"));
        assertEquals("YES", column.get("is_nullable"));
    }

    @Test
    void anActivityKeepsItsLessonIdAndOldActivitiesStillWork() {
        var withLesson = newActivity();
        var lessonId = UUID.randomUUID();
        withLesson.setLessonId(lessonId);
        var withoutLesson = newActivity();
        flushAndClear();

        assertEquals(lessonId, activities.findById(withLesson.getId()).orElseThrow().getLessonId());
        assertEquals(null, activities.findById(withoutLesson.getId()).orElseThrow().getLessonId());
    }

    @Test
    void listsOnlyActiveActivitiesByTopic() {
        var active = newActivity();
        var inactive = newActivity();
        inactive.setIsActive(false);
        flushAndClear();

        var ids = activities.findAllByIsActiveTrueOrderByTitle().stream().map(ActivityEntity::getId).toList();
        var ofTopic = activities.findAllByTopicIdAndIsActiveTrueOrderByTitle(active.getTopic().getId());

        assertTrue(ids.contains(active.getId()));
        assertTrue(!ids.contains(inactive.getId()));
        assertEquals(1, ofTopic.size());
        assertTrue(activities.findByIdAndIsActiveTrue(active.getId()).isPresent());
        assertTrue(activities.findByIdAndIsActiveTrue(inactive.getId()).isEmpty());
    }

    @Test
    void listsTopicsByName() {
        var zebra = new TopicEntity();
        zebra.setName("zzz-last");
        var alpha = new TopicEntity();
        alpha.setName("aaa-first");
        topics.save(zebra);
        topics.save(alpha);
        flushAndClear();

        var names = topics.findAllByOrderByName().stream().map(TopicEntity::getName).toList();

        assertTrue(names.indexOf("aaa-first") < names.indexOf("zzz-last"));
    }

    private AssignedActivityEntity assign(ChildEntity child, ActivityEntity activity, ParentEntity parent, Instant assignedAt) {
        var assignment = new AssignedActivityEntity();
        assignment.setChild(child);
        assignment.setActivity(activity);
        assignment.setAssignedBy(parent);
        assignment.setAssignedAt(assignedAt);
        return assignments.save(assignment);
    }

    @Test
    void pagesAssignmentsByChildAndByParentNewestFirst() {
        var parent = newParent();
        var other = newParent();
        var child = newChild(parent);
        var sibling = newChild(parent);
        var stranger = newChild(other);
        var activity = newActivity();
        var oldest = assign(child, activity, parent, Instant.parse("2026-01-01T00:00:00Z"));
        var newest = assign(child, activity, parent, Instant.parse("2026-03-01T00:00:00Z"));
        assign(sibling, activity, parent, Instant.parse("2026-02-01T00:00:00Z"));
        assign(stranger, activity, other, Instant.parse("2026-02-01T00:00:00Z"));
        flushAndClear();

        var ofChild = assignments.findByChildId(child.getId(), org.springframework.data.domain.PageRequest.of(0, 10,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "assignedAt")));
        var ofParent = assignments.findByChildParentId(parent.getId(), org.springframework.data.domain.PageRequest.of(0, 2,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "assignedAt")));

        assertEquals(java.util.List.of(newest.getId(), oldest.getId()), ofChild.getContent().stream().map(AssignedActivityEntity::getId).toList());
        assertEquals(3, ofParent.getTotalElements());
        assertEquals(2, ofParent.getTotalPages());
        assertEquals(2, ofParent.getContent().size());
        assertEquals("Balance your screen time", ofChild.getContent().get(0).getActivity().getTitle());
    }

    @Test
    void anAssignmentIsOpenUntilTheChildCompletesIt() {
        var parent = newParent();
        var child = newChild(parent);
        var otherChild = newChild(parent);
        var activity = newActivity();
        var assignment = assign(child, activity, parent, Instant.now());
        flushAndClear();

        assertTrue(assignments.existsOpenAssignment(child.getId(), activity.getId()));
        assertTrue(!assignments.existsOpenAssignment(otherChild.getId(), activity.getId()));

        var progress = new ActivityProgressEntity();
        progress.setAssignment(assignments.getReferenceById(assignment.getId()));
        progress.setCompletionPercentage(50);
        progresses.save(progress);
        flushAndClear();
        assertTrue(assignments.existsOpenAssignment(child.getId(), activity.getId()));

        var stored = progresses.findByAssignmentId(assignment.getId()).orElseThrow();
        stored.setCompletionPercentage(100);
        stored.setCompletionTime(Instant.now());
        flushAndClear();

        assertTrue(!assignments.existsOpenAssignment(child.getId(), activity.getId()));
    }

    @Test
    void findsTheProgressOfSeveralAssignmentsAtOnce() {
        var parent = newParent();
        var child = newChild(parent);
        var activity = newActivity();
        var started = assign(child, activity, parent, Instant.now());
        var notStarted = assign(child, activity, parent, Instant.now());
        var progress = new ActivityProgressEntity();
        progress.setAssignment(started);
        progress.setCompletionPercentage(10);
        progresses.save(progress);
        flushAndClear();

        var found = progresses.findAllByAssignmentIdIn(java.util.List.of(started.getId(), notStarted.getId()));

        assertEquals(1, found.size());
        assertEquals(started.getId(), found.get(0).getAssignment().getId());
        assertTrue(progresses.findByAssignmentId(notStarted.getId()).isEmpty());
    }

    @Test
    void loadsAnAssignmentForUpdateInsideATransaction() {
        var parent = newParent();
        var child = newChild(parent);
        var assignment = assign(child, newActivity(), parent, Instant.now());
        flushAndClear();

        var locked = assignments.findForUpdateById(assignment.getId());

        assertTrue(locked.isPresent());
        assertEquals(child.getId(), locked.get().getChild().getId());
        assertTrue(assignments.findForUpdateById(UUID.randomUUID()).isEmpty());
    }

    private PointEntity point(ChildEntity child, int amount, Instant at) {
        var point = new PointEntity();
        point.setChild(child);
        point.setAmount(amount);
        point.setSourceType("QUIZ");
        point.setSourceId(UUID.randomUUID());
        point.setTransactionDate(at);
        point.setTransactionType(TransactionType.EARN);
        return points.save(point);
    }

    @Test
    void pagesThePointsOfAChildNewestFirstWithoutMixingChildren() {
        var parent = newParent();
        var child = newChild(parent);
        var other = newChild(parent);
        var oldest = point(child, 10, Instant.parse("2026-01-01T00:00:00Z"));
        var middle = point(child, 20, Instant.parse("2026-02-01T00:00:00Z"));
        var newest = point(child, 30, Instant.parse("2026-03-01T00:00:00Z"));
        point(other, 99, Instant.parse("2026-04-01T00:00:00Z"));
        flushAndClear();

        var first = points.findByChildId(child.getId(), org.springframework.data.domain.PageRequest.of(0, 2,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "transactionDate")));
        var second = points.findByChildId(child.getId(), org.springframework.data.domain.PageRequest.of(1, 2,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "transactionDate")));

        assertEquals(3, first.getTotalElements());
        assertEquals(java.util.List.of(newest.getId(), middle.getId()), first.getContent().stream().map(PointEntity::getId).toList());
        assertEquals(java.util.List.of(oldest.getId()), second.getContent().stream().map(PointEntity::getId).toList());
    }

    @Test
    void listsTheEarnedAchievementsNewestFirstWithTheirAchievementLoaded() {
        var parent = newParent();
        var child = newChild(parent);
        var other = newChild(parent);
        var first = new AchievementEntity();
        first.setName("First lesson");
        first.setDescription("d");
        first.setPointsValue(10);
        first.setRequirements("one lesson");
        achievements.save(first);
        var second = new AchievementEntity();
        second.setName("Three lessons");
        second.setDescription("d");
        second.setPointsValue(30);
        second.setRequirements("three lessons");
        achievements.save(second);
        for (var spec : java.util.List.of(java.util.Map.entry(first, "2026-01-01T00:00:00Z"), java.util.Map.entry(second, "2026-02-01T00:00:00Z"))) {
            var earned = new ChildAchievementEntity();
            earned.setChild(child);
            earned.setAchievement(spec.getKey());
            earned.setEarnedAt(Instant.parse(spec.getValue()));
            childAchievements.save(earned);
        }
        var foreign = new ChildAchievementEntity();
        foreign.setChild(other);
        foreign.setAchievement(first);
        foreign.setEarnedAt(Instant.now());
        childAchievements.save(foreign);
        flushAndClear();

        var found = childAchievements.findByChildIdOrderByEarnedAtDesc(child.getId());

        assertEquals(java.util.List.of("Three lessons", "First lesson"), found.stream().map(e -> e.getAchievement().getName()).toList());
        assertTrue(childAchievements.findByChildIdOrderByEarnedAtDesc(UUID.randomUUID()).isEmpty());
    }

    private NotificationEntity notification(UUID userId, boolean read, Instant createdAt) {
        var notification = new NotificationEntity();
        notification.setUserId(userId);
        notification.setType(NotificationType.SYSTEM);
        notification.setTitle("t");
        notification.setMessage("m");
        notification.setCreatedAt(createdAt);
        notification.setIsRead(read);
        return notifications.save(notification);
    }

    @Test
    void pagesTheInboxOfAUserNewestFirstAndFiltersTheUnread() {
        var user = UUID.randomUUID();
        var oldest = notification(user, true, Instant.parse("2026-01-01T00:00:00Z"));
        var middle = notification(user, false, Instant.parse("2026-02-01T00:00:00Z"));
        var newest = notification(user, false, Instant.parse("2026-03-01T00:00:00Z"));
        notification(UUID.randomUUID(), false, Instant.parse("2026-04-01T00:00:00Z"));
        flushAndClear();
        var newestFirst = org.springframework.data.domain.PageRequest.of(0, 10,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        var all = notifications.findByUserId(user, newestFirst);
        var unread = notifications.findByUserIdAndIsReadFalse(user, newestFirst);

        assertEquals(java.util.List.of(newest.getId(), middle.getId(), oldest.getId()), all.getContent().stream().map(NotificationEntity::getId).toList());
        assertEquals(java.util.List.of(newest.getId(), middle.getId()), unread.getContent().stream().map(NotificationEntity::getId).toList());
        assertEquals(2, notifications.countByUserIdAndIsReadFalse(user));
        assertEquals(0, notifications.countByUserIdAndIsReadFalse(UUID.randomUUID()));
    }

    @Test
    void aNotificationIsOnlyFoundForItsOwner() {
        var owner = UUID.randomUUID();
        var saved = notification(owner, false, Instant.now());
        flushAndClear();

        assertTrue(notifications.findByIdAndUserId(saved.getId(), owner).isPresent());
        assertTrue(notifications.findByIdAndUserId(saved.getId(), UUID.randomUUID()).isEmpty());
    }

    @Test
    void markAllReadOnlyChangesTheUnreadOnesOfThatUser() {
        var user = UUID.randomUUID();
        var other = UUID.randomUUID();
        var alreadyRead = notification(user, true, Instant.now());
        alreadyRead.setReadAt(Instant.parse("2026-01-01T00:00:00Z"));
        notification(user, false, Instant.now());
        notification(user, false, Instant.now());
        var foreign = notification(other, false, Instant.now());
        flushAndClear();
        var readAt = Instant.parse("2026-10-21T12:00:00Z");

        var changed = notifications.markAllRead(user, readAt);
        flushAndClear();

        assertEquals(2, changed);
        assertEquals(0, notifications.countByUserIdAndIsReadFalse(user));
        assertEquals(1, notifications.countByUserIdAndIsReadFalse(other));
        assertTrue(notifications.findById(foreign.getId()).orElseThrow().getReadAt() == null);
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), notifications.findById(alreadyRead.getId()).orElseThrow().getReadAt());
        assertEquals(0, notifications.markAllRead(user, readAt));
    }
}
