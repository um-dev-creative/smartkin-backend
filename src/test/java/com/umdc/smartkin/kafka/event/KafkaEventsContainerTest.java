package com.umdc.smartkin.kafka.event;

import com.umdc.smartkin.kafka.event.payload.QuizCompletedPayload;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Publishes through a real broker and reads the record back the way a consumer (KF-2, mercury) would.
@Testcontainers
class KafkaEventsContainerTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");

    @Test
    void aPublishedEventReachesTheTopicWithTheChildAsKey() throws Exception {
        var mapper = JsonMapper.builder().build();
        var topics = new KafkaTopicsProperties("it.assigned", "it.quiz", "it.achievement");
        var producerFactory = new DefaultKafkaProducerFactory<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        var publisher = new SmartKinEventPublisher(new KafkaTemplate<>(producerFactory), mapper, topics);
        var child = UUID.randomUUID();
        var payload = new QuizCompletedPayload(UUID.randomUUID(), child, UUID.randomUUID(), UUID.randomUUID(), 1, 7, 10, 70, true,
                Instant.parse("2026-10-18T12:00:00Z"));

        publisher.quizCompleted(payload).get(30, TimeUnit.SECONDS);

        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of("it.quiz"));
            var records = consumer.poll(Duration.ofSeconds(20));
            if (records.isEmpty()) {
                records = consumer.poll(Duration.ofSeconds(20));
            }

            assertEquals(1, records.count());
            var record = records.iterator().next();
            assertEquals(child.toString(), record.key());
            SmartKinEvent<QuizCompletedPayload> event = mapper.readValue(record.value(), new TypeReference<>() {
            });
            assertEquals(EventType.QUIZ_COMPLETED, event.type());
            assertEquals(payload, event.payload());
        }
        producerFactory.destroy();
    }
}
