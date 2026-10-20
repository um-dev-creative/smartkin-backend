package com.umdc.smartkin.jpa.domain;

/// Kind of in-app notification. Maps the PostgreSQL enum `smartkin.notification_type`.
/// The constants are stored by name, matching the labels created by the Flyway baseline.
public enum NotificationType {
    ASSIGNMENT,
    REMINDER,
    ACHIEVEMENT,
    REWARD,
    GOAL,
    SYSTEM;
}
