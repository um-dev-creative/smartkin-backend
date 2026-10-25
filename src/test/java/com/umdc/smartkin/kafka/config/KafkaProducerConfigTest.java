package com.umdc.smartkin.kafka.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KafkaProducerConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaProducerConfig.class)
            .withPropertyValues("prx.bootstrap.server.url=broker", "prx.bootstrap.server.port=9093");

    @Test
    void theProducerKeepsItsPlaintextConfigurationByDefault() {
        runner.run(context -> {
            var configuration = context.getBean(ProducerFactory.class).getConfigurationProperties();
            assertEquals("broker:9093", configuration.get("bootstrap.servers"));
            assertFalse(configuration.containsKey("security.protocol"));
            assertNotNull(context.getBean(KafkaTemplate.class));
        });
    }

    @Test
    void theSecuritySettingsReachTheProducer() {
        runner.withPropertyValues(
                "prx.kafka.security.protocol=SSL",
                "prx.kafka.security.ssl.truststore-location=file:/k/ca.pem",
                "prx.kafka.security.ssl.keystore-location=/k/client.pem").run(context -> {
            var configuration = context.getBean(ProducerFactory.class).getConfigurationProperties();
            assertEquals("SSL", configuration.get("security.protocol"));
            assertEquals("/k/ca.pem", configuration.get("ssl.truststore.location"));
            assertEquals("/k/client.pem", configuration.get("ssl.keystore.location"));
            assertEquals("broker:9093", configuration.get("bootstrap.servers"));
        });
    }
}
