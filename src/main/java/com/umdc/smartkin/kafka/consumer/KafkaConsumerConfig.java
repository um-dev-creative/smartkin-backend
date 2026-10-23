package com.umdc.smartkin.kafka.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.apache.kafka.common.TopicPartition;
import tools.jackson.core.JacksonException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Consumer side of Kafka: a String/String consumer factory and a listener container factory whose error handler retries with
/// exponential back-off and then moves the record to the error topic.
@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaConsumerProperties.class)
@ConditionalOnProperty(prefix = "prx.kafka.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerConfig {

    private final String brokers;

    public KafkaConsumerConfig(@Value("${prx.bootstrap.server.url}") String bootstrapServers,
                               @Value("${prx.bootstrap.server.port:}") String bootstrapServerPort) {
        this.brokers = bootstrapServerPort.isEmpty() ? bootstrapServers : String.format("%s:%s", bootstrapServers, bootstrapServerPort);
    }

    @Bean
    public ConsumerFactory<String, String> smartKinConsumerFactory(KafkaConsumerProperties properties) {
        Map<String, Object> config = new ConcurrentHashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.groupId());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public DefaultErrorHandler smartKinErrorHandler(KafkaOperations<String, String> kafkaTemplate, KafkaConsumerProperties properties,
                                                    MeterRegistry meterRegistry) {
        var monitor = new ErrorTopicMonitor(meterRegistry);
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, error) -> new TopicPartition(properties.errorTopic(), -1));
        var handler = new DefaultErrorHandler(monitor.monitoring(recoverer), backOff(properties));
        handler.setRetryListeners(monitor);
        // A malformed record fails the same way every time; do not wait for retries that cannot help.
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> smartKinConsumerFactory, DefaultErrorHandler smartKinErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(smartKinConsumerFactory);
        factory.setCommonErrorHandler(smartKinErrorHandler);
        return factory;
    }

    /// The back-off for `maxAttempts` total tries: the first delivery plus `maxAttempts - 1` retries.
    static ExponentialBackOff backOff(KafkaConsumerProperties properties) {
        var backOff = new ExponentialBackOff(properties.initialInterval().toMillis(), properties.multiplier());
        backOff.setMaxInterval(properties.maxInterval().toMillis());
        backOff.setMaxAttempts(Math.max(properties.maxAttempts() - 1, 0));
        return backOff;
    }
}
