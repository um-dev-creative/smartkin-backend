package com.umdc.smartkin.kafka.config;

import io.jsonwebtoken.lang.Objects;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(KafkaSecurityProperties.class)
public class KafkaProducerConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerConfig.class);
    private static final int MAX_BLOCK_MS = 10_000;

    @Value("${umdc.bootstrap.server.url}")
    private String bootstrapServers;

    @Value("${umdc.bootstrap.server.port}")
    private String bootstrapServerPort;

    @Bean
    public ProducerFactory<String, String> producerFactory(KafkaSecurityProperties security) {
        logger.info("Creating Producer Factory");
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                !Objects.isEmpty(bootstrapServerPort) ? String.format("%s:%s", bootstrapServers, bootstrapServerPort) : bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // A topic that does not exist makes send() wait max.block.ms (60 s by default) for its metadata while the visitor waits; fail fast.
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS);
        configProps.putAll(security.clientProperties());
        logger.info("Producer Factory created");
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        logger.info("Creating Kafka Template");
        return new KafkaTemplate<>(producerFactory);
    }
}
