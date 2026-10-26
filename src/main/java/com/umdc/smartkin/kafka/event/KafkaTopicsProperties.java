package com.umdc.smartkin.kafka.event;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// Topic names of the SmartKin domain events (`umdc.kafka.topics.*`). The `.v1` suffix is the schema version of the payloads.
///
/// @param activityAssigned  topic of [EventType#ACTIVITY_ASSIGNED]
/// @param quizCompleted     topic of [EventType#QUIZ_COMPLETED]
/// @param achievementEarned topic of [EventType#ACHIEVEMENT_EARNED]
@ConfigurationProperties("umdc.kafka.topics")
public record KafkaTopicsProperties(
        @DefaultValue("smartkin.activity.assigned.v1") String activityAssigned,
        @DefaultValue("smartkin.quiz.completed.v1") String quizCompleted,
        @DefaultValue("smartkin.achievement.earned.v1") String achievementEarned) {

    /// @return the topic the given event type is published to
    public String topicFor(EventType type) {
        return switch (type) {
            case ACTIVITY_ASSIGNED -> activityAssigned;
            case QUIZ_COMPLETED -> quizCompleted;
            case ACHIEVEMENT_EARNED -> achievementEarned;
        };
    }
}
