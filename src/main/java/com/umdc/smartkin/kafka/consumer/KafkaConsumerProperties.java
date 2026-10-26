package com.umdc.smartkin.kafka.consumer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/// Consumer settings (`umdc.kafka.consumer.*`).
///
/// @param enabled         turns the listeners off, e.g. for a deployment that only produces
/// @param groupId         consumer group of the listeners
/// @param errorTopic      where records that could not be processed end up, with the exception in the record headers
/// @param maxAttempts     total tries per record (the first one plus the retries) before it goes to the error topic
/// @param initialInterval wait before the first retry
/// @param multiplier      growth of the wait between retries
/// @param maxInterval     upper bound of the wait between retries
@ConfigurationProperties("umdc.kafka.consumer")
public record KafkaConsumerProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("smartkin-backend") String groupId,
        @DefaultValue("smartkin.events.error.v1") String errorTopic,
        @DefaultValue("4") int maxAttempts,
        @DefaultValue("1s") Duration initialInterval,
        @DefaultValue("2.0") double multiplier,
        @DefaultValue("30s") Duration maxInterval) {
}
