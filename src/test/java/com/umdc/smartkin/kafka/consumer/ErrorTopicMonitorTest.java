package com.umdc.smartkin.kafka.consumer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ErrorTopicMonitorTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ErrorTopicMonitor monitor = new ErrorTopicMonitor(registry);
    private final ConsumerRecord<String, String> record = new ConsumerRecord<>("smartkin.quiz.completed.v1", 2, 41L, "child", "{}");

    @Test
    void countsEveryFailedDeliveryPerTopic() {
        monitor.failedDelivery(record, new IllegalStateException("db down"), 1);
        monitor.failedDelivery(record, new IllegalStateException("db down"), 2);
        monitor.failedDelivery(new ConsumerRecord<>("other.topic", 0, 0L, "k", "v"), new IllegalStateException("x"), 1);

        assertEquals(2.0, registry.get(ErrorTopicMonitor.DELIVERY_FAILED).tag("topic", "smartkin.quiz.completed.v1").counter().count());
        assertEquals(1.0, registry.get(ErrorTopicMonitor.DELIVERY_FAILED).tag("topic", "other.topic").counter().count());
    }

    @Test
    void aRecordSentToTheErrorTopicIsCountedByTopicAndCause() {
        var published = new java.util.ArrayList<ConsumerRecord<?, ?>>();
        var recoverer = monitor.monitoring((failed, error) -> published.add(failed));

        recoverer.accept(record, new org.springframework.kafka.listener.ListenerExecutionFailedException("listener failed", new IllegalStateException("db down")));
        recoverer.accept(record, new IllegalArgumentException("bad payload"));

        assertEquals(2, published.size());
        assertSame(record, published.get(0));
        assertEquals(1.0, registry.get(ErrorTopicMonitor.DEAD_LETTERED).tag("exception", "IllegalStateException").counter().count());
        assertEquals(1.0, registry.get(ErrorTopicMonitor.DEAD_LETTERED).tag("exception", "IllegalArgumentException").counter().count());
        assertNull(registry.find(ErrorTopicMonitor.DEAD_LETTER_FAILED).counter());
    }

    @Test
    void whenTheErrorTopicCannotBeWrittenTheFailureIsCountedAndPropagates() {
        var recoverer = monitor.monitoring((failed, error) -> {
            throw new IllegalStateException("topic does not exist");
        });

        var thrown = assertThrows(IllegalStateException.class, () -> recoverer.accept(record, new IllegalStateException("db down")));

        assertEquals("topic does not exist", thrown.getMessage());
        assertEquals(1.0, registry.get(ErrorTopicMonitor.DEAD_LETTER_FAILED).tag("topic", "smartkin.quiz.completed.v1").counter().count());
        assertNull(registry.find(ErrorTopicMonitor.DEAD_LETTERED).counter());
    }
}
