package com.umdc.smartkin.jpa.domain;

/// Lifecycle of a reward a child redeems. Maps the PostgreSQL enum `smartkin.reward_status`.
/// The constants are stored by name, matching the labels created by the Flyway baseline.
public enum RewardStatus {
    PENDING,
    APPROVED,
    DELIVERED,
    REJECTED;
}
