package com.umdc.smartkin.achievement;

import com.umdc.smartkin.kafka.event.DomainEventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/// Starts an [AchievementEvaluator] run from places that must never fail because of it: a problem while evaluating is logged and the next
/// trigger (the evaluation is idempotent and counts from scratch) catches up.
@Component
public class AchievementTrigger {

    private static final Logger logger = LoggerFactory.getLogger(AchievementTrigger.class);

    private final AchievementEvaluator evaluator;
    private final DomainEventDispatcher dispatcher;

    /// Creates the trigger.
    ///
    /// @param evaluator  the evaluator
    /// @param dispatcher runs work after the current transaction commits
    public AchievementTrigger(AchievementEvaluator evaluator, DomainEventDispatcher dispatcher) {
        this.evaluator = evaluator;
        this.dispatcher = dispatcher;
    }

    /// Evaluates now, swallowing failures.
    ///
    /// @param childId the child
    public void evaluateSafely(UUID childId) {
        try {
            evaluator.evaluate(childId);
        } catch (RuntimeException e) {
            logger.error("Could not evaluate the achievements of child {}: {}", childId, e.getMessage(), e);
        }
    }

    /// Evaluates once the current transaction commits (at once when there is none), so the evaluation sees what the caller just wrote.
    ///
    /// @param childId the child
    public void evaluateAfterCommit(UUID childId) {
        dispatcher.afterCommit("achievements of " + childId, () -> evaluateSafely(childId));
    }
}
