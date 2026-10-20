package com.umdc.smartkin.kafka.event;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/// The envelope every SmartKin domain event travels in.
///
/// @param eventId       unique per event; consumers use it to ignore redeliveries
/// @param type          which event this is; selects the topic and the payload shape
/// @param schemaVersion version of the payload layout; raised only for breaking changes (also part of the topic name)
/// @param occurredAt    when the fact happened, UTC
/// @param subjectId     the child the event is about (a backbone user id); it is the Kafka key, so a child's events stay in order
/// @param payload       the event data, one of the records in `com.umdc.smartkin.kafka.event.payload`
public record SmartKinEvent<T>(UUID eventId, EventType type, int schemaVersion, Instant occurredAt, UUID subjectId, T payload) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /// Builds an event stamped with a new id, the clock's current time and the current schema version.
    public static <T> SmartKinEvent<T> of(EventType type, UUID subjectId, T payload, Clock clock) {
        return new SmartKinEvent<>(UUID.randomUUID(), type, CURRENT_SCHEMA_VERSION, clock.instant(), subjectId, payload);
    }
}
