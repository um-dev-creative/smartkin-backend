package com.umdc.smartkin.kafka.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.RetryListener;

/// Makes the consumer's failures visible so someone can alert on them (BE-14). It counts every failed delivery (a record that is being
/// retried) and every record that gave up and went to the error topic, and logs the latter at `ERROR` with the stable marker
/// `DEAD_LETTERED` plus topic, partition, offset, key and cause. If publishing to the error topic itself fails (for example because the topic
/// does not exist) it counts that too and lets the error propagate, so the container does not commit the offset and the record is not lost.
///
/// Metrics: `smartkin.events.delivery_failed` (tag `topic`), `smartkin.events.dead_lettered` (tags `topic`, `exception`) and
/// `smartkin.events.dead_letter_failed` (tag `topic`).
public class ErrorTopicMonitor implements RetryListener {

    static final String DELIVERY_FAILED = "smartkin.events.delivery_failed";
    static final String DEAD_LETTERED = "smartkin.events.dead_lettered";
    static final String DEAD_LETTER_FAILED = "smartkin.events.dead_letter_failed";

    private static final Logger logger = LoggerFactory.getLogger(ErrorTopicMonitor.class);

    private final MeterRegistry registry;

    /// Creates the monitor.
    ///
    /// @param registry where the counters are registered
    public ErrorTopicMonitor(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void failedDelivery(ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt) {
        registry.counter(DELIVERY_FAILED, "topic", record.topic()).increment();
        logger.warn("Delivery {} of {}-{}@{} failed: {}", deliveryAttempt, record.topic(), record.partition(), record.offset(), exception.getMessage());
    }

    /// Wraps the recoverer that publishes to the error topic.
    ///
    /// @param delegate the recoverer that does the publishing
    /// @return a recoverer that counts and logs around it
    public ConsumerRecordRecoverer monitoring(ConsumerRecordRecoverer delegate) {
        return (record, exception) -> {
            var cause = exception.getCause() == null ? exception : exception.getCause();
            try {
                delegate.accept(record, exception);
            } catch (RuntimeException e) {
                registry.counter(DEAD_LETTER_FAILED, "topic", record.topic()).increment();
                logger.error("DEAD_LETTER_FAILED {}-{}@{} key={}: could not publish to the error topic: {}", record.topic(), record.partition(),
                        record.offset(), record.key(), e.getMessage(), e);
                throw e;
            }
            registry.counter(DEAD_LETTERED, "topic", record.topic(), "exception", cause.getClass().getSimpleName()).increment();
            logger.error("DEAD_LETTERED {}-{}@{} key={} cause={}: {}", record.topic(), record.partition(), record.offset(), record.key(),
                    cause.getClass().getName(), cause.getMessage());
        };
    }
}
