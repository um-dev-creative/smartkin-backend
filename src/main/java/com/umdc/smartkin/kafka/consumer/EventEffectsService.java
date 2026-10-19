package com.umdc.smartkin.kafka.consumer;

import com.umdc.smartkin.jpa.domain.NotificationEntity;
import com.umdc.smartkin.jpa.domain.NotificationType;
import com.umdc.smartkin.jpa.domain.PointEntity;
import com.umdc.smartkin.jpa.domain.TransactionType;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.NotificationRepository;
import com.umdc.smartkin.jpa.repository.PointRepository;
import com.umdc.smartkin.kafka.event.SmartKinEvent;
import com.umdc.smartkin.kafka.event.payload.AchievementEarnedPayload;
import com.umdc.smartkin.kafka.event.payload.ActivityAssignedPayload;
import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/// Applies the domain events to the PostgreSQL model: points, child totals and the in-app inbox.
///
/// Kafka delivers at least once, so every method first checks whether the effect already exists and does nothing if it does.
/// Each method is one transaction: either all the effects of an event are stored or none, and a failure is retried by the
/// consumer's error handler.
@Service
public class EventEffectsService {

    static final String SOURCE_QUIZ = "QUIZ";
    static final String SOURCE_ACHIEVEMENT = "ACHIEVEMENT";

    private static final Logger logger = LoggerFactory.getLogger(EventEffectsService.class);

    private final PointRepository points;
    private final NotificationRepository notifications;
    private final ChildRepository children;

    public EventEffectsService(PointRepository points, NotificationRepository notifications, ChildRepository children) {
        this.points = points;
        this.notifications = notifications;
        this.children = children;
    }

    /// Tells the child about the new assignment.
    @Transactional
    public void onActivityAssigned(SmartKinEvent<ActivityAssignedPayload> event) {
        var payload = event.payload();
        notify(payload.childId(), NotificationType.ASSIGNMENT, "Nueva actividad",
                "Tienes una nueva actividad: " + payload.activityTitle(), payload.assignmentId(), event.occurredAt());
    }

    /// Books the score of a passed quiz as points; a failed attempt earns nothing.
    @Transactional
    public void onQuizCompleted(SmartKinEvent<QuizCompletedPayload> event) {
        var payload = event.payload();
        if (!payload.passed() || payload.score() <= 0) {
            logger.debug("Quiz attempt {} earns no points", payload.attemptId());
            return;
        }
        earn(payload.childId(), payload.score(), SOURCE_QUIZ, payload.attemptId(), "Quiz aprobado", event.occurredAt());
    }

    /// Books the achievement's points and tells the child.
    @Transactional
    public void onAchievementEarned(SmartKinEvent<AchievementEarnedPayload> event) {
        var payload = event.payload();
        if (payload.pointsValue() > 0) {
            earn(payload.childId(), payload.pointsValue(), SOURCE_ACHIEVEMENT, payload.childAchievementId(),
                    "Logro: " + payload.achievementName(), event.occurredAt());
        }
        notify(payload.childId(), NotificationType.ACHIEVEMENT, "¡Nuevo logro!",
                "Conseguiste el logro " + payload.achievementName(), payload.childAchievementId(), event.occurredAt());
    }

    private void earn(UUID childId, int amount, String sourceType, UUID sourceId, String description, Instant at) {
        if (points.existsBySourceTypeAndSourceIdAndTransactionType(sourceType, sourceId, TransactionType.EARN)) {
            logger.info("Points of {} {} already booked", sourceType, sourceId);
            return;
        }
        var child = children.findById(childId)
                .orElseThrow(() -> new IllegalStateException("Child " + childId + " is not registered in SmartKin"));
        var point = new PointEntity();
        point.setChild(child);
        point.setAmount(amount);
        point.setSourceType(sourceType);
        point.setSourceId(sourceId);
        point.setTransactionType(TransactionType.EARN);
        point.setDescription(description);
        point.setTransactionDate(at);
        points.save(point);
        child.setTotalPoints(child.getTotalPoints() + amount);
        children.save(child);
    }

    private void notify(UUID userId, NotificationType type, String title, String message, UUID relatedId, Instant at) {
        if (notifications.existsByUserIdAndTypeAndRelatedEntityId(userId, type, relatedId)) {
            logger.info("Notification {} about {} already exists", type, relatedId);
            return;
        }
        var notification = new NotificationEntity();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRelatedEntityId(relatedId);
        notification.setCreatedAt(at);
        notifications.save(notification);
    }
}
