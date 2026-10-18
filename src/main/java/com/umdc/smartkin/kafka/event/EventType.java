package com.umdc.smartkin.kafka.event;

/// The domain events SmartKin publishes. Each type has its own topic (see `KafkaTopicsProperties`) and a payload record in
/// `com.umdc.smartkin.kafka.event.payload`.
public enum EventType {
    /// A parent assigned an activity to a child.
    ACTIVITY_ASSIGNED,
    /// A child finished the quiz of a lesson (passed or not).
    QUIZ_COMPLETED,
    /// A child earned an achievement.
    ACHIEVEMENT_EARNED
}
