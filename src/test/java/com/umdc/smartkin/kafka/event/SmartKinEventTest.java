package com.umdc.smartkin.kafka.event;

import com.umdc.smartkin.kafka.event.payload.AchievementEarnedPayload;
import com.umdc.smartkin.kafka.event.payload.ActivityAssignedPayload;
import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartKinEventTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final UUID child = UUID.randomUUID();

    private QuizCompletedPayload quiz() {
        return new QuizCompletedPayload(UUID.randomUUID(), child, UUID.randomUUID(), UUID.randomUUID(), 2, 8, 10, 80, true, NOW);
    }

    @Test
    void theFactoryStampsIdTimeAndSchemaVersion() {
        var first = SmartKinEvent.of(EventType.QUIZ_COMPLETED, child, quiz(), CLOCK);
        var second = SmartKinEvent.of(EventType.QUIZ_COMPLETED, child, quiz(), CLOCK);

        assertEquals(NOW, first.occurredAt());
        assertEquals(SmartKinEvent.CURRENT_SCHEMA_VERSION, first.schemaVersion());
        assertEquals(child, first.subjectId());
        assertNotEquals(first.eventId(), second.eventId());
    }

    @Test
    void theEnvelopeSerializesWithIsoDatesAndReadsBack() {
        var event = SmartKinEvent.of(EventType.QUIZ_COMPLETED, child, quiz(), CLOCK);

        var json = mapper.writeValueAsString(event);
        SmartKinEvent<QuizCompletedPayload> back = mapper.readValue(json, new TypeReference<>() {
        });

        assertTrue(json.contains("\"type\":\"QUIZ_COMPLETED\""), json);
        assertTrue(json.contains("\"schemaVersion\":1"), json);
        assertTrue(json.contains("\"occurredAt\":\"2026-10-18T12:00:00Z\""), json);
        assertEquals(event, back);
    }

    @Test
    void allThreePayloadsRoundTrip() {
        var assigned = new ActivityAssignedPayload(UUID.randomUUID(), child, UUID.randomUUID(), UUID.randomUUID(), "Screen time", NOW);
        var earned = new AchievementEarnedPayload(UUID.randomUUID(), child, UUID.randomUUID(), "First lesson", 10, NOW);

        assertEquals(assigned, mapper.readValue(mapper.writeValueAsString(assigned), ActivityAssignedPayload.class));
        assertEquals(earned, mapper.readValue(mapper.writeValueAsString(earned), AchievementEarnedPayload.class));
    }

    @Test
    void topicsDefaultToTheVersionedNames() {
        var topics = new Binder(new MapConfigurationPropertySource(Map.of())).bindOrCreate("prx.kafka.topics", KafkaTopicsProperties.class);

        assertEquals("smartkin.activity.assigned.v1", topics.topicFor(EventType.ACTIVITY_ASSIGNED));
        assertEquals("smartkin.quiz.completed.v1", topics.topicFor(EventType.QUIZ_COMPLETED));
        assertEquals("smartkin.achievement.earned.v1", topics.topicFor(EventType.ACHIEVEMENT_EARNED));
    }

    @Test
    void topicNamesCanBeOverriddenByConfiguration() {
        var source = new MapConfigurationPropertySource(Map.of("prx.kafka.topics.quiz-completed", "custom.quiz"));

        var topics = new Binder(source).bindOrCreate("prx.kafka.topics", KafkaTopicsProperties.class);

        assertEquals("custom.quiz", topics.topicFor(EventType.QUIZ_COMPLETED));
        assertEquals("smartkin.activity.assigned.v1", topics.topicFor(EventType.ACTIVITY_ASSIGNED));
    }
}
