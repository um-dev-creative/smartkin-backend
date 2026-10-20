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
}
