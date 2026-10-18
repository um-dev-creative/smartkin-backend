package com.umdc.smartkin.kafka.event;

import com.umdc.smartkin.kafka.event.payload.AchievementEarnedPayload;
import com.umdc.smartkin.kafka.event.payload.ActivityAssignedPayload;
import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartKinEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final KafkaTopicsProperties topics = new KafkaTopicsProperties("t.assigned", "t.quiz", "t.achievement");
    private final UUID child = UUID.randomUUID();

    private SmartKinEventPublisher publisher() {
        return new SmartKinEventPublisher(kafkaTemplate, JsonMapper.builder().build(), topics);
    }

    @SuppressWarnings("unchecked")
    private void sendSucceeds() {
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    @Test
    void quizCompletedGoesToItsTopicKeyedByTheChild() {
        sendSucceeds();
        var payload = new QuizCompletedPayload(UUID.randomUUID(), child, UUID.randomUUID(), UUID.randomUUID(), 1, 9, 10, 90, true, Instant.now());

        publisher().quizCompleted(payload);

        var json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("t.quiz"), eq(child.toString()), json.capture());
        assertTrue(json.getValue().contains("\"type\":\"QUIZ_COMPLETED\""));
        assertTrue(json.getValue().contains(payload.attemptId().toString()));
    }

    @Test
    void activityAssignedAndAchievementEarnedUseTheirOwnTopics() {
        sendSucceeds();
        var assigned = new ActivityAssignedPayload(UUID.randomUUID(), child, UUID.randomUUID(), UUID.randomUUID(), "Screen time", null);
        var earned = new AchievementEarnedPayload(UUID.randomUUID(), child, UUID.randomUUID(), "First lesson", 10, Instant.now());

        publisher().activityAssigned(assigned);
        publisher().achievementEarned(earned);

        verify(kafkaTemplate).send(eq("t.assigned"), eq(child.toString()), any(String.class));
        verify(kafkaTemplate).send(eq("t.achievement"), eq(child.toString()), any(String.class));
    }

    @Test
    void aFailedSendCompletesTheFutureExceptionallyInsteadOfThrowing() {
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        var payload = new AchievementEarnedPayload(UUID.randomUUID(), child, UUID.randomUUID(), "First lesson", 10, Instant.now());

        var future = publisher().achievementEarned(payload);

        var failure = assertThrows(ExecutionException.class, future::get);
        assertEquals("broker down", failure.getCause().getMessage());
    }
}
