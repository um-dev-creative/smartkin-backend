package com.umdc.smartkin.kafka.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.util.backoff.BackOffExecution;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaConsumerConfig.class)
            .withBean(KafkaOperations.class, () -> mock(KafkaOperations.class))
            .withBean(io.micrometer.core.instrument.MeterRegistry.class, io.micrometer.core.instrument.simple.SimpleMeterRegistry::new)
            .withPropertyValues("umdc.bootstrap.server.url=broker");

    @Test
    void defaultsMatchTheDocumentedValues() {
        var defaults = new KafkaConsumerProperties(true, "smartkin-backend", "smartkin.events.error.v1", 4,
                Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30));
        runner.run(context -> assertEquals(defaults, context.getBean(KafkaConsumerProperties.class)));
    }

    @Test
    void backOffRetriesOneLessThanTheAttemptsAndGrowsUpToTheCap() {
        var properties = new KafkaConsumerProperties(true, "g", "e", 4, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(3));

        BackOffExecution execution = KafkaConsumerConfig.backOff(properties).start();

        assertEquals(1000, execution.nextBackOff());
        assertEquals(2000, execution.nextBackOff());
        assertEquals(3000, execution.nextBackOff());
        assertEquals(BackOffExecution.STOP, execution.nextBackOff());
    }

    @Test
    void oneAttemptMeansNoRetries() {
        var properties = new KafkaConsumerProperties(true, "g", "e", 1, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(3));

        assertEquals(BackOffExecution.STOP, KafkaConsumerConfig.backOff(properties).start().nextBackOff());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsTheConsumerFactoryWithTheBrokerAndGroup() {
        runner.withPropertyValues("umdc.bootstrap.server.port=9092", "umdc.kafka.consumer.group-id=my-group").run(context -> {
            var factory = context.getBean(ConsumerFactory.class);
            assertEquals("broker:9092", factory.getConfigurationProperties().get("bootstrap.servers"));
            assertEquals("my-group", factory.getConfigurationProperties().get("group.id"));
            assertNotNull(context.getBean(ConcurrentKafkaListenerContainerFactory.class));
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void theConsumerStaysPlaintextByDefaultAndTakesTheSecuritySettingsWhenSet() {
        runner.run(context -> assertFalse(
                context.getBean(ConsumerFactory.class).getConfigurationProperties().containsKey("security.protocol")));

        runner.withPropertyValues(
                "umdc.kafka.security.protocol=SASL_SSL",
                "umdc.kafka.security.sasl.jaas-config=login",
                "umdc.kafka.security.ssl.truststore-location=/k/ca.pem").run(context -> {
            var configuration = context.getBean(ConsumerFactory.class).getConfigurationProperties();
            assertEquals("SASL_SSL", configuration.get("security.protocol"));
            assertEquals("login", configuration.get("sasl.jaas.config"));
            assertEquals("/k/ca.pem", configuration.get("ssl.truststore.location"));
            assertEquals("broker", configuration.get("bootstrap.servers"));
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesTheBareUrlWhenThereIsNoPort() {
        runner.run(context -> assertEquals("broker",
                context.getBean(ConsumerFactory.class).getConfigurationProperties().get("bootstrap.servers")));
    }

    @Test
    void thereAreNoConsumersWhenDisabled() {
        runner.withPropertyValues("umdc.kafka.consumer.enabled=false").run(context ->
                assertFalse(context.containsBean("kafkaListenerContainerFactory")));
    }

    @Test
    void malformedRecordsAreNotRetried() {
        runner.run(context -> {
            var handler = context.getBean(org.springframework.kafka.listener.DefaultErrorHandler.class);
            // removeClassification returns the previous value: FALSE means "not retryable"
            assertEquals(Boolean.FALSE, handler.removeClassification(tools.jackson.core.JacksonException.class));
            assertEquals(Boolean.FALSE, handler.removeClassification(IllegalArgumentException.class));
        });
    }
}
