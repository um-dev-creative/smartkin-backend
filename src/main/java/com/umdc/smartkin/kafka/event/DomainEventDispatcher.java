package com.umdc.smartkin.kafka.event;

import com.umdc.smartkin.kafka.event.payload.AchievementEarnedPayload;
import com.umdc.smartkin.kafka.event.payload.ActivityAssignedPayload;
import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/// Publishes domain events on behalf of the services, **after the database transaction commits** (nothing is sent on a rollback) and without
/// ever failing the caller: the data is already committed, so a broker failure is only logged. There is no transactional outbox yet (D-4),
/// which means a failed publish is not retried and the consumers' side effects (inbox notification, points) are missing for that event.
/// Without an active transaction the event is published at once.
@Component
public class DomainEventDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(DomainEventDispatcher.class);

    private final SmartKinEventPublisher publisher;

    /// Creates the dispatcher.
    ///
    /// @param publisher the Kafka publisher
    public DomainEventDispatcher(SmartKinEventPublisher publisher) {
        this.publisher = publisher;
    }

    /// Publishes `ACTIVITY_ASSIGNED` once the current transaction commits.
    ///
    /// @param payload the event payload
    public void activityAssigned(ActivityAssignedPayload payload) {
        afterCommit(EventType.ACTIVITY_ASSIGNED.name() + " " + payload.assignmentId(), () -> publisher.activityAssigned(payload));
    }

    /// Publishes `QUIZ_COMPLETED` once the current transaction commits.
    ///
    /// @param payload the event payload
    public void quizCompleted(QuizCompletedPayload payload) {
        afterCommit(EventType.QUIZ_COMPLETED.name() + " " + payload.attemptId(), () -> publisher.quizCompleted(payload));
    }

    /// Publishes `ACHIEVEMENT_EARNED` once the current transaction commits.
    ///
    /// @param payload the event payload
    public void achievementEarned(AchievementEarnedPayload payload) {
        afterCommit(EventType.ACHIEVEMENT_EARNED.name() + " " + payload.childAchievementId(), () -> publisher.achievementEarned(payload));
    }

    /// Runs `action` once the current transaction commits (at once when there is none). The action never fails the caller:
    /// an exception is logged.
    ///
    /// @param label what the action does, for the log
    /// @param action the work
    public void afterCommit(String label, Runnable action) {
        Runnable guarded = () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                logger.error("Could not run after commit {}: {}", label, e.getMessage(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    guarded.run();
                }
            });
        } else {
            guarded.run();
        }
    }
}
