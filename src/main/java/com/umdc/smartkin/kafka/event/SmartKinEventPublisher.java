package com.umdc.smartkin.kafka.event;

import com.umdc.smartkin.kafka.event.payload.AchievementEarnedPayload;
import com.umdc.smartkin.kafka.event.payload.ActivityAssignedPayload;
import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.concurrent.CompletableFuture;

/// Publishes SmartKin domain events as JSON to their topics, keyed by the child so one child's events keep their order.
///
/// Publishing is not transactional with the database: if the process dies after a commit and before the send, the event is lost.
/// The returned future lets callers react to a failed send; a transactional outbox is the way to remove that gap if it matters.
@Service
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class SmartKinEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(SmartKinEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;
    private final KafkaTopicsProperties topics;
    private final Clock clock;

    public SmartKinEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper mapper, KafkaTopicsProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = mapper;
        this.topics = topics;
        this.clock = Clock.systemUTC();
    }

    /// Publishes an event to the topic of its type, with the subject (the child) as the record key.
    ///
    /// @return the send result; it completes exceptionally when Kafka rejects the record
    public CompletableFuture<SendResult<String, String>> publish(SmartKinEvent<?> event) {
        String topic = topics.topicFor(event.type());
        String json = mapper.writeValueAsString(event);
        return kafkaTemplate.send(topic, event.subjectId().toString(), json).whenComplete((result, error) -> {
            if (error == null) {
                logger.debug("Published {} {} to {}", event.type(), event.eventId(), topic);
            } else {
                logger.error("Could not publish {} {} to {}: {}", event.type(), event.eventId(), topic, error.getMessage(), error);
            }
        });
    }

    public CompletableFuture<SendResult<String, String>> activityAssigned(ActivityAssignedPayload payload) {
        return publish(SmartKinEvent.of(EventType.ACTIVITY_ASSIGNED, payload.childId(), payload, clock));
    }

    public CompletableFuture<SendResult<String, String>> quizCompleted(QuizCompletedPayload payload) {
        return publish(SmartKinEvent.of(EventType.QUIZ_COMPLETED, payload.childId(), payload, clock));
    }

    public CompletableFuture<SendResult<String, String>> achievementEarned(AchievementEarnedPayload payload) {
        return publish(SmartKinEvent.of(EventType.ACHIEVEMENT_EARNED, payload.childId(), payload, clock));
    }
}
