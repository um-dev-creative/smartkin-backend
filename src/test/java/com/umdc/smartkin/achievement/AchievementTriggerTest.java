package com.umdc.smartkin.achievement;

import com.umdc.smartkin.kafka.event.DomainEventDispatcher;
import com.umdc.smartkin.kafka.event.SmartKinEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementTriggerTest {

    private static final UUID CHILD = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Mock
    private AchievementEvaluator evaluator;
    @Mock
    private SmartKinEventPublisher publisher;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evaluatesNow() {
        var trigger = new AchievementTrigger(evaluator, new DomainEventDispatcher(publisher));
        when(evaluator.evaluate(CHILD)).thenReturn(List.of());

        trigger.evaluateSafely(CHILD);

        verify(evaluator).evaluate(CHILD);
    }

    @Test
    void aFailingEvaluationNeverReachesTheCaller() {
        var trigger = new AchievementTrigger(evaluator, new DomainEventDispatcher(publisher));
        when(evaluator.evaluate(CHILD)).thenThrow(new IllegalStateException("mongo down"));

        assertDoesNotThrow(() -> trigger.evaluateSafely(CHILD));
    }

    @Test
    void evaluatesAfterTheCallersTransactionCommits() {
        var trigger = new AchievementTrigger(evaluator, new DomainEventDispatcher(publisher));
        TransactionSynchronizationManager.initSynchronization();

        trigger.evaluateAfterCommit(CHILD);
        verify(evaluator, never()).evaluate(CHILD);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(evaluator).evaluate(CHILD);
    }

    @Test
    void evaluatesAtOnceWhenThereIsNoTransaction() {
        var trigger = new AchievementTrigger(evaluator, new DomainEventDispatcher(publisher));

        trigger.evaluateAfterCommit(CHILD);

        verify(evaluator).evaluate(CHILD);
    }
}
