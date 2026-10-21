package com.umdc.smartkin.kafka.consumer;

import com.umdc.smartkin.achievement.AchievementTrigger;
import com.umdc.smartkin.kafka.event.EventType;
import com.umdc.smartkin.kafka.event.SmartKinEvent;
import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/// Runs the real listeners and error handler against a real broker: a failing record is retried and then lands in the error topic,
/// a malformed one lands there without retries, and the records after them are still processed.
@Testcontainers
class ConsumerRetryContainerTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");

    @Configuration
    @Import({KafkaConsumerConfig.class, SmartKinEventListener.class})
    static class Wiring {
        static final AtomicInteger calls = new AtomicInteger();
        static final EventEffectsService effects = mock(EventEffectsService.class);

        @Bean
        ObjectMapper mapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        EventEffectsService effects() {
            return effects;
        }

        @Bean
        AchievementTrigger achievementTrigger() {
            return mock(AchievementTrigger.class);
        }

        @Bean
        KafkaOperations<String, String> kafkaTemplate() {
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
        }
    }

    @Test
    void failingRecordsAreRetriedThenMovedToTheErrorTopic() throws Exception {
        var run = UUID.randomUUID().toString().substring(0, 8);
        var quizTopic = "smartkin.quiz.completed.v1";
        var errorTopic = "it.error." + run;
        var mapper = JsonMapper.builder().build();
        var child = UUID.randomUUID();
        var good = event(child, 9);
        var failing = event(child, 1);
        doAnswer(invocation -> {
            SmartKinEvent<QuizCompletedPayload> event = invocation.getArgument(0);
            Wiring.calls.incrementAndGet();
            if (event.payload().score() == 1) {
                throw new IllegalStateException("database unavailable");
            }
            return null;
        }).when(Wiring.effects).onQuizCompleted(any());

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("it", Map.of(
                    "prx.bootstrap.server.url", kafka.getBootstrapServers(),
                    "prx.kafka.consumer.group-id", "it-" + run,
                    "prx.kafka.consumer.error-topic", errorTopic,
                    "prx.kafka.consumer.max-attempts", "3",
                    "prx.kafka.consumer.initial-interval", "100ms",
                    "prx.kafka.consumer.max-interval", "200ms")));
            context.register(Wiring.class);
            context.refresh();
            var template = context.getBean(KafkaOperations.class);
            template.send(quizTopic, child.toString(), "not json").get();
            template.send(quizTopic, child.toString(), mapper.writeValueAsString(failing)).get();
            template.send(quizTopic, child.toString(), mapper.writeValueAsString(good)).get();

            try (var consumer = new KafkaConsumer<String, String>(Map.of(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                    ConsumerConfig.GROUP_ID_CONFIG, "it-reader-" + run,
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
                consumer.subscribe(List.of(errorTopic));
                var dead = new java.util.ArrayList<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
                var deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
                while (dead.size() < 2 && System.nanoTime() < deadline) {
                    consumer.poll(Duration.ofSeconds(2)).forEach(dead::add);
                }

                assertEquals(2, dead.size());
                assertEquals("not json", dead.get(0).value());
                assertEquals(mapper.writeValueAsString(failing), dead.get(1).value());
                assertEquals(child.toString(), dead.get(1).key());
                var cause = dead.get(1).headers().lastHeader("kafka_dlt-exception-message");
                assertNotNull(cause);
                assertTrue(new String(cause.value(), StandardCharsets.UTF_8).contains("database unavailable"));
            }
            // 3 attempts for the failing record (0 for the malformed one) and 1 for the good one, which was not blocked.
            var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (Wiring.calls.get() < 4 && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            assertEquals(4, Wiring.calls.get());
        }
    }

    private SmartKinEvent<QuizCompletedPayload> event(UUID child, int score) {
        return SmartKinEvent.of(EventType.QUIZ_COMPLETED, child, new QuizCompletedPayload(UUID.randomUUID(), child, UUID.randomUUID(),
                UUID.randomUUID(), 1, score, 10, score * 10, true, Instant.parse("2026-10-18T12:00:00Z")), Clock.systemUTC());
    }
}
