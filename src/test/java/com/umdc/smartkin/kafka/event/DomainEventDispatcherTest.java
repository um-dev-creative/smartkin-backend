package com.umdc.smartkin.kafka.event;

import com.umdc.smartkin.kafka.event.payload.AchievementEarnedPayload;
import com.umdc.smartkin.kafka.event.payload.ActivityAssignedPayload;
import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainEventDispatcherTest {

    private static final UUID ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final ActivityAssignedPayload ASSIGNED = new ActivityAssignedPayload(ID, ID, ID, ID, "Passwords", null);
    private static final QuizCompletedPayload COMPLETED = new QuizCompletedPayload(ID, ID, ID, ID, 1, 10, 10, 100, true, Instant.parse("2026-10-21T12:00:00Z"));

    private static final AchievementEarnedPayload EARNED = new AchievementEarnedPayload(ID, ID, ID, "Explorer", 20, Instant.parse("2026-10-21T12:00:00Z"));

    @Mock
    private SmartKinEventPublisher publisher;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishesAtOnceWhenThereIsNoTransaction() {
        var dispatcher = new DomainEventDispatcher(publisher);

        dispatcher.activityAssigned(ASSIGNED);
        dispatcher.quizCompleted(COMPLETED);

        verify(publisher).activityAssigned(ASSIGNED);
        verify(publisher).quizCompleted(COMPLETED);
    }

    @Test
    void publishesOnlyAfterTheTransactionCommits() {
        var dispatcher = new DomainEventDispatcher(publisher);
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.activityAssigned(ASSIGNED);
        dispatcher.quizCompleted(COMPLETED);
        verify(publisher, never()).activityAssigned(any());
        verify(publisher, never()).quizCompleted(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(publisher).activityAssigned(ASSIGNED);
        verify(publisher).quizCompleted(COMPLETED);
    }

    @Test
    void publishesNothingWhenTheTransactionRollsBack() {
        var dispatcher = new DomainEventDispatcher(publisher);
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.activityAssigned(ASSIGNED);
        dispatcher.quizCompleted(COMPLETED);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(publisher, never()).activityAssigned(any());
        verify(publisher, never()).quizCompleted(any());
    }

    @Test
    void aFailingBrokerNeverFailsTheCaller() {
        when(publisher.activityAssigned(ASSIGNED)).thenThrow(new IllegalStateException("broker down"));
        when(publisher.quizCompleted(COMPLETED)).thenThrow(new IllegalStateException("broker down"));
        var dispatcher = new DomainEventDispatcher(publisher);

        dispatcher.activityAssigned(ASSIGNED);
        dispatcher.quizCompleted(COMPLETED);

        verify(publisher).activityAssigned(ASSIGNED);
        verify(publisher).quizCompleted(COMPLETED);
    }

    @Test
    void achievementsGoOutAfterTheCommitToo() {
        var dispatcher = new DomainEventDispatcher(publisher);
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.achievementEarned(EARNED);
        verify(publisher, never()).achievementEarned(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(publisher).achievementEarned(EARNED);
    }

    @Test
    void anyWorkCanRunAfterTheCommitAndNeverFailsTheCaller() {
        var dispatcher = new DomainEventDispatcher(publisher);
        var ran = new java.util.concurrent.atomic.AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.afterCommit("work", ran::incrementAndGet);
        dispatcher.afterCommit("broken work", () -> {
            throw new IllegalStateException("boom");
        });
        org.junit.jupiter.api.Assertions.assertEquals(0, ran.get());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        org.junit.jupiter.api.Assertions.assertEquals(1, ran.get());
    }
}
