package com.umdc.smartkin.kafka.consumer;

import com.umdc.smartkin.kafka.event.EventType;
import com.umdc.smartkin.kafka.event.SmartKinEvent;
import com.umdc.smartkin.kafka.event.payload.AchievementEarnedPayload;
import com.umdc.smartkin.kafka.event.payload.ActivityAssignedPayload;
import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SmartKinEventListenerTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final EventEffectsService effects = mock(EventEffectsService.class);
    private final SmartKinEventListener listener = new SmartKinEventListener(mapper, effects);
    private final UUID child = UUID.randomUUID();

    @Test
    void forwardsAnAssignmentWithItsPayload() {
        var payload = new ActivityAssignedPayload(UUID.randomUUID(), child, UUID.randomUUID(), UUID.randomUUID(), "Lesson", NOW);
        var event = SmartKinEvent.of(EventType.ACTIVITY_ASSIGNED, child, payload, CLOCK);

        listener.onActivityAssigned(mapper.writeValueAsString(event));

        verify(effects).onActivityAssigned(event);
    }

    @Test
    void forwardsAQuizResultWithItsPayload() {
        var payload = new QuizCompletedPayload(UUID.randomUUID(), child, UUID.randomUUID(), UUID.randomUUID(), 1, 7, 10, 70, true, NOW);
        var event = SmartKinEvent.of(EventType.QUIZ_COMPLETED, child, payload, CLOCK);

        listener.onQuizCompleted(mapper.writeValueAsString(event));

        verify(effects).onQuizCompleted(event);
    }

    @Test
    void forwardsAnAchievementWithItsPayload() {
        var payload = new AchievementEarnedPayload(UUID.randomUUID(), child, UUID.randomUUID(), "Explorer", 20, NOW);
        var event = SmartKinEvent.of(EventType.ACHIEVEMENT_EARNED, child, payload, CLOCK);

        listener.onAchievementEarned(mapper.writeValueAsString(event));

        verify(effects).onAchievementEarned(event);
    }

    @Test
    void rejectsJsonThatIsNotAnEvent() {
        assertThrows(JacksonException.class, () -> listener.onQuizCompleted("not json"));

        verifyNoInteractions(effects);
    }

    @Test
    void rejectsAnEventWithoutPayload() {
        var json = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"QUIZ_COMPLETED\",\"schemaVersion\":1}";

        var error = assertThrows(IllegalArgumentException.class, () -> listener.onQuizCompleted(json));

        assertEquals("The event has no payload", error.getMessage());
        verifyNoInteractions(effects);
    }

    @Test
    void rejectsTheLiteralNull() {
        assertThrows(IllegalArgumentException.class, () -> listener.onActivityAssigned("null"));

        verify(effects, org.mockito.Mockito.never()).onActivityAssigned(any());
    }
}
