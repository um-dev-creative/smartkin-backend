package com.umdc.smartkin.jpa.domain;

/// State of a goal set for a child. Maps the PostgreSQL enum `smartkin.goal_status`.
/// The constants are stored by name, matching the labels created by the Flyway baseline.
public enum GoalStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED;
}
