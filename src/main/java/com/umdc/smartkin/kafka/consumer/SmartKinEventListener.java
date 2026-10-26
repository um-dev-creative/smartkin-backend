package com.umdc.smartkin.kafka.consumer;

import com.umdc.smartkin.achievement.AchievementTrigger;
import com.umdc.smartkin.kafka.event.SmartKinEvent;
import com.umdc.smartkin.kafka.event.payload.AchievementEarnedPayload;
import com.umdc.smartkin.kafka.event.payload.ActivityAssignedPayload;
import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/// Reads the SmartKin domain events from their topics and hands them to [EventEffectsService].
///
/// A record that cannot be parsed or processed throws; the container's error handler (see [KafkaConsumerConfig]) retries it and
/// finally moves it to the error topic, so one bad record never blocks the partition.
@Component
@ConditionalOnProperty(prefix = "umdc.kafka.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SmartKinEventListener {

    private final ObjectMapper mapper;
    private final EventEffectsService effects;
    private final AchievementTrigger achievements;

    public SmartKinEventListener(ObjectMapper mapper, EventEffectsService effects, AchievementTrigger achievements) {
        this.mapper = mapper;
        this.effects = effects;
        this.achievements = achievements;
    }

    @KafkaListener(topics = "${umdc.kafka.topics.activity-assigned:smartkin.activity.assigned.v1}")
    public void onActivityAssigned(String json) {
        effects.onActivityAssigned(parse(json, ActivityAssignedPayload.class));
    }

    @KafkaListener(topics = "${umdc.kafka.topics.quiz-completed:smartkin.quiz.completed.v1}")
    public void onQuizCompleted(String json) {
        var event = parse(json, QuizCompletedPayload.class);
        effects.onQuizCompleted(event);
        if (event.payload().passed()) {
            achievements.evaluateSafely(event.payload().childId());
        }
    }

    @KafkaListener(topics = "${umdc.kafka.topics.achievement-earned:smartkin.achievement.earned.v1}")
    public void onAchievementEarned(String json) {
        var event = parse(json, AchievementEarnedPayload.class);
        effects.onAchievementEarned(event);
        achievements.evaluateSafely(event.payload().childId());
    }

    private <T> SmartKinEvent<T> parse(String json, Class<T> payloadType) {
        JavaType type = mapper.getTypeFactory().constructParametricType(SmartKinEvent.class, payloadType);
        SmartKinEvent<T> event = mapper.readValue(json, type);
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("The event has no payload");
        }
        return event;
    }
}
