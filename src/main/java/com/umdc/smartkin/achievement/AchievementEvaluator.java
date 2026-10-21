package com.umdc.smartkin.achievement;

import com.umdc.smartkin.jpa.domain.AchievementEntity;
import com.umdc.smartkin.jpa.repository.AchievementRepository;
import com.umdc.smartkin.jpa.repository.ActivityProgressRepository;
import com.umdc.smartkin.jpa.repository.ChildAchievementRepository;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.kafka.event.DomainEventDispatcher;
import com.umdc.smartkin.kafka.event.payload.AchievementEarnedPayload;
import com.umdc.smartkin.mongo.document.QuizAttemptDocument;
import com.umdc.smartkin.mongo.repository.QuizAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Awards the achievements a child has earned. It runs in its **own transaction** (`REQUIRES_NEW`) so it sees what the triggering transaction
/// already committed and so a failure here never rolls the caller back. Every rule is checked against fresh counts; an achievement already
/// earned is skipped, and the award is an `insert ... on conflict do nothing` so it happens once per child even if two evaluations race.
/// For each new award `ACHIEVEMENT_EARNED` is published after the commit; its consumer books the points and notifies the child, and
/// that may in turn complete a `POINTS_REACHED` rule, which the next evaluation picks up.
@Service
public class AchievementEvaluator {

    private final AchievementRepository achievementRepository;
    private final ChildAchievementRepository childAchievementRepository;
    private final ChildRepository childRepository;
    private final ActivityProgressRepository progressRepository;
    private final QuizAttemptRepository attemptRepository;
    private final DomainEventDispatcher events;
    private final ObjectMapper mapper;
    private final Clock clock;

    /// Creates the evaluator.
    ///
    /// @param achievementRepository       the achievement catalog
    /// @param childAchievementRepository  the earned achievements
    /// @param childRepository             the children (point balance)
    /// @param progressRepository          completed activities
    /// @param attemptRepository           quiz attempts (MongoDB)
    /// @param events                      publishes `ACHIEVEMENT_EARNED` after the commit
    /// @param mapper                      reads the rules
    /// @param clock                       the current time
    public AchievementEvaluator(AchievementRepository achievementRepository, ChildAchievementRepository childAchievementRepository,
                                ChildRepository childRepository, ActivityProgressRepository progressRepository,
                                QuizAttemptRepository attemptRepository, DomainEventDispatcher events, ObjectMapper mapper, Clock clock) {
        this.achievementRepository = achievementRepository;
        this.childAchievementRepository = childAchievementRepository;
        this.childRepository = childRepository;
        this.progressRepository = progressRepository;
        this.attemptRepository = attemptRepository;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    /// Awards every achievement the child has now earned.
    ///
    /// @param childId the child
    /// @return the ids of the achievements awarded by this call (empty when none, or when the child is unknown)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> evaluate(UUID childId) {
        var child = childRepository.findById(childId);
        if (child.isEmpty()) {
            return List.of();
        }
        Set<UUID> earned = new HashSet<>(childAchievementRepository.findAchievementIdsByChildId(childId));
        Map<AchievementRuleType, Integer> counts = new ConcurrentHashMap<>();
        var awarded = new ArrayList<UUID>();
        for (AchievementEntity achievement : achievementRepository.findAll()) {
            if (earned.contains(achievement.getId())) {
                continue;
            }
            var rule = AchievementRule.parse(achievement.getRequirements(), mapper);
            if (rule.isEmpty()) {
                continue;
            }
            int count = counts.computeIfAbsent(rule.get().type(), type -> count(type, childId, child.get().getTotalPoints()));
            if (count >= rule.get().threshold() && award(childId, achievement)) {
                awarded.add(achievement.getId());
            }
        }
        return List.copyOf(awarded);
    }

    private boolean award(UUID childId, AchievementEntity achievement) {
        var childAchievementId = UUID.randomUUID();
        var now = clock.instant();
        if (childAchievementRepository.insertIfAbsent(childAchievementId, childId, achievement.getId(), now) == 0) {
            return false;
        }
        events.achievementEarned(new AchievementEarnedPayload(childAchievementId, childId, achievement.getId(), achievement.getName(),
                achievement.getPointsValue(), now));
        return true;
    }

    private int count(AchievementRuleType type, UUID childId, int points) {
        return switch (type) {
            case QUIZZES_PASSED -> (int) attemptRepository.findByChildIdAndPassedTrue(childId).stream()
                    .map(QuizAttemptDocument::lessonId).distinct().count();
            case ACTIVITIES_COMPLETED -> (int) progressRepository.countCompletedActivities(childId);
            case POINTS_REACHED -> points;
        };
    }
}
