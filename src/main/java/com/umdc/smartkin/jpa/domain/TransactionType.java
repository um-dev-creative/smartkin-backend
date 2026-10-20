package com.umdc.smartkin.jpa.domain;

/// Direction of a points movement. Maps the PostgreSQL enum `smartkin.transaction_type`.
/// The constants are stored by name, matching the labels created by the Flyway baseline.
public enum TransactionType {
    EARN,
    SPEND;
}
