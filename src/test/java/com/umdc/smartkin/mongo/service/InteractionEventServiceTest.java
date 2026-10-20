package com.umdc.smartkin.mongo.service;

import com.umdc.smartkin.mongo.document.InteractionEventDocument;
import com.umdc.smartkin.mongo.repository.InteractionEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractionEventServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private InteractionEventRepository repository;

    private final UUID child = UUID.randomUUID();
    private final UUID lesson = UUID.randomUUID();

    @Test
    void stampsTheEventWithTheRetentionDeadline() {
        var service = new InteractionEventService(repository, CLOCK, Duration.ofDays(90));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = service.record(child, lesson, null, "scene-1", "drag-1", "DROP", Map.of("x", 10));

        assertEquals(NOW, saved.occurredAt());
        assertEquals(NOW.plus(Duration.ofDays(90)), saved.expiresAt());
        assertEquals(child, saved.childId());
        assertEquals("DROP", saved.eventType());
        assertEquals(Map.of("x", 10), saved.payload());
    }

    @Test
    void storesAnEmptyPayloadWhenNoneIsGiven() {
        var service = new InteractionEventService(repository, CLOCK, Duration.ofDays(1));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var attempt = UUID.randomUUID();

        service.record(child, lesson, attempt, "scene-1", "tap-1", "TAP", null);

        var captor = ArgumentCaptor.forClass(InteractionEventDocument.class);
        verify(repository).save(captor.capture());
        assertEquals(Map.of(), captor.getValue().payload());
        assertEquals(attempt, captor.getValue().attemptId());
    }

    @Test
    void returnsWhatTheRepositorySaved() {
        var service = new InteractionEventService(repository, CLOCK, Duration.ofDays(1));
        var stored = new InteractionEventDocument(UUID.randomUUID(), child, lesson, null, "s", "i", "TAP", Map.of(), NOW, NOW);
        when(repository.save(any())).thenReturn(stored);

        assertSame(stored, service.record(child, lesson, null, "s", "i", "TAP", Map.of()));
    }

    @Test
    void usesTheSystemClockByDefault() {
        var service = new InteractionEventService(repository, Duration.ofHours(1));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = service.record(child, lesson, null, "s", "i", "TAP", Map.of());

        assertEquals(Duration.ofHours(1), Duration.between(saved.occurredAt(), saved.expiresAt()));
    }

    @Test
    void rejectsAMissingOrNonPositiveRetention() {
        assertThrows(IllegalArgumentException.class, () -> new InteractionEventService(repository, CLOCK, null));
        assertThrows(IllegalArgumentException.class, () -> new InteractionEventService(repository, CLOCK, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new InteractionEventService(repository, CLOCK, Duration.ofDays(-1)));
    }
}
