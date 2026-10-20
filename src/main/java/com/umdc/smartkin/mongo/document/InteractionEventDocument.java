package com.umdc.smartkin.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/// A raw interaction the child had with a lesson scene, stored in `interaction_events` for later analysis.
/// MongoDB deletes the document when `expiresAt` passes (TTL index); see `InteractionEventService` for the retention.
@Document(collection = "interaction_events")
@CompoundIndex(name = "child_lesson_time", def = "{'childId': 1, 'lessonId': 1, 'occurredAt': -1}")
public record InteractionEventDocument(
        @Id UUID id,
        UUID childId,
        UUID lessonId,
        @Indexed UUID attemptId,
        String sceneId,
        String interactionId,
        String eventType,
        Map<String, Object> payload,
        Instant occurredAt,
        @Indexed(name = "expires_at_ttl", expireAfter = "0s") Instant expiresAt) {
}
