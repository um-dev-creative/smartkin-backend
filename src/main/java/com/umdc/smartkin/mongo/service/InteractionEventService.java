package com.umdc.smartkin.mongo.service;

import com.umdc.smartkin.mongo.document.InteractionEventDocument;
import com.umdc.smartkin.mongo.repository.InteractionEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// Records the raw interaction events of a lesson and stamps each one with the date MongoDB will expire it.
@Service
public class InteractionEventService {

    private final InteractionEventRepository repository;
    private final Clock clock;
    private final Duration retention;

    @Autowired
    public InteractionEventService(InteractionEventRepository repository,
                                   @Value("${umdc.interaction-events.retention:90d}") Duration retention) {
        this(repository, Clock.systemUTC(), retention);
    }

    InteractionEventService(InteractionEventRepository repository, Clock clock, Duration retention) {
        if (Objects.isNull(retention) || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("The interaction events retention must be positive");
        }
        this.repository = repository;
        this.clock = clock;
        this.retention = retention;
    }

    /// Stores one event with `occurredAt` set to now and `expiresAt` to now plus the configured retention.
    ///
    /// @param attemptId the quiz attempt the event belongs to, or `null` while the child is still in the animation
    /// @param payload   event details; a `null` payload is stored as an empty map
    public InteractionEventDocument record(UUID childId, UUID lessonId, UUID attemptId, String sceneId,
                                           String interactionId, String eventType, Map<String, Object> payload) {
        Instant now = clock.instant();
        var event = new InteractionEventDocument(UUID.randomUUID(), childId, lessonId, attemptId, sceneId, interactionId,
                eventType, Objects.isNull(payload) ? Map.of() : payload, now, now.plus(retention));
        return repository.save(event);
    }
}
