package com.umdc.smartkin.kafka.consumer;

import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.NotificationEntity;
import com.umdc.smartkin.jpa.domain.NotificationType;
import com.umdc.smartkin.jpa.domain.PointEntity;
import com.umdc.smartkin.jpa.domain.TransactionType;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.NotificationRepository;
import com.umdc.smartkin.jpa.repository.PointRepository;
import com.umdc.smartkin.kafka.event.EventType;
import com.umdc.smartkin.kafka.event.SmartKinEvent;
import com.umdc.smartkin.kafka.event.payload.AchievementEarnedPayload;
import com.umdc.smartkin.kafka.event.payload.ActivityAssignedPayload;
import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventEffectsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private PointRepository points;
    @Mock
    private NotificationRepository notifications;
    @Mock
    private ChildRepository children;

    private EventEffectsService service;
    private final UUID childId = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EventEffectsService(points, notifications, children);
    }

    private ChildEntity child(int total) {
        var child = new ChildEntity();
        child.setId(childId);
        child.setTotalPoints(total);
        return child;
    }

    private SmartKinEvent<QuizCompletedPayload> quiz(boolean passed, int score) {
        return SmartKinEvent.of(EventType.QUIZ_COMPLETED, childId,
                new QuizCompletedPayload(sourceId, childId, UUID.randomUUID(), UUID.randomUUID(), 1, score, 10, score * 10, passed, NOW), CLOCK);
    }

    private SmartKinEvent<AchievementEarnedPayload> achievement(int pointsValue) {
        return SmartKinEvent.of(EventType.ACHIEVEMENT_EARNED, childId,
                new AchievementEarnedPayload(sourceId, childId, UUID.randomUUID(), "Explorer", pointsValue, NOW), CLOCK);
    }

    private SmartKinEvent<ActivityAssignedPayload> assignment() {
        return SmartKinEvent.of(EventType.ACTIVITY_ASSIGNED, childId,
                new ActivityAssignedPayload(sourceId, childId, UUID.randomUUID(), UUID.randomUUID(), "Screen time", NOW), CLOCK);
    }

    @Test
    void assignmentCreatesAnInboxNotificationForTheChild() {
        when(notifications.existsByUserIdAndTypeAndRelatedEntityId(childId, NotificationType.ASSIGNMENT, sourceId)).thenReturn(false);

        service.onActivityAssigned(assignment());

        var captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notifications).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(childId, saved.getUserId());
        assertEquals(NotificationType.ASSIGNMENT, saved.getType());
        assertEquals(sourceId, saved.getRelatedEntityId());
        assertEquals(NOW, saved.getCreatedAt());
        assertEquals("Tienes una nueva actividad: Screen time", saved.getMessage());
    }

    @Test
    void aRedeliveredAssignmentDoesNotNotifyTwice() {
        when(notifications.existsByUserIdAndTypeAndRelatedEntityId(childId, NotificationType.ASSIGNMENT, sourceId)).thenReturn(true);

        service.onActivityAssigned(assignment());

        verify(notifications, never()).save(any());
    }

    @Test
    void aPassedQuizBooksItsScoreAsPoints() {
        var child = child(5);
        when(points.existsBySourceTypeAndSourceIdAndTransactionType("QUIZ", sourceId, TransactionType.EARN)).thenReturn(false);
        when(children.findById(childId)).thenReturn(Optional.of(child));

        service.onQuizCompleted(quiz(true, 8));

        var captor = ArgumentCaptor.forClass(PointEntity.class);
        verify(points).save(captor.capture());
        assertEquals(8, captor.getValue().getAmount());
        assertEquals("QUIZ", captor.getValue().getSourceType());
        assertEquals(sourceId, captor.getValue().getSourceId());
        assertEquals(TransactionType.EARN, captor.getValue().getTransactionType());
        assertEquals(NOW, captor.getValue().getTransactionDate());
        assertEquals(13, child.getTotalPoints());
        verify(children).save(child);
    }

    @Test
    void aFailedQuizEarnsNothing() {
        service.onQuizCompleted(quiz(false, 4));

        verifyNoInteractions(points, children, notifications);
    }

    @Test
    void aPassedQuizWithoutScoreEarnsNothing() {
        service.onQuizCompleted(quiz(true, 0));

        verifyNoInteractions(points, children, notifications);
    }

    @Test
    void aRedeliveredQuizDoesNotBookPointsTwice() {
        when(points.existsBySourceTypeAndSourceIdAndTransactionType("QUIZ", sourceId, TransactionType.EARN)).thenReturn(true);

        service.onQuizCompleted(quiz(true, 8));

        verify(points, never()).save(any());
        verifyNoInteractions(children);
    }

    @Test
    void anUnknownChildFailsSoTheConsumerRetriesAndFinallyDeadLetters() {
        when(children.findById(childId)).thenReturn(Optional.empty());

        var error = assertThrows(IllegalStateException.class, () -> service.onQuizCompleted(quiz(true, 8)));

        assertEquals("Child " + childId + " is not registered in SmartKin", error.getMessage());
        verify(points, never()).save(any());
    }

    @Test
    void anAchievementBooksItsPointsAndNotifiesTheChild() {
        var child = child(0);
        when(children.findById(childId)).thenReturn(Optional.of(child));

        service.onAchievementEarned(achievement(20));

        var point = ArgumentCaptor.forClass(PointEntity.class);
        verify(points).save(point.capture());
        assertEquals(20, point.getValue().getAmount());
        assertEquals("ACHIEVEMENT", point.getValue().getSourceType());
        assertEquals(20, child.getTotalPoints());
        var notification = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notifications).save(notification.capture());
        assertEquals(NotificationType.ACHIEVEMENT, notification.getValue().getType());
        assertEquals("Conseguiste el logro Explorer", notification.getValue().getMessage());
    }

    @Test
    void anAchievementWithoutPointsOnlyNotifies() {
        service.onAchievementEarned(achievement(0));

        verifyNoInteractions(points, children);
        verify(notifications).save(any(NotificationEntity.class));
    }
}
