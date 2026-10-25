package com.umdc.smartkin.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// The TLS bundle and the Vault / Config Server clients read the keystores from the classpath by default (the JVM jar carries them)
/// and from an explicit URL when `SSL_KEYSTORE_URL` / `SSL_TRUSTSTORE_URL` is set (the native binary does not embed them).
class SslLocationPropertiesTest {

    private static PropertySourcesPropertyResolver resolver(Map<String, Object> env) throws IOException {
        var sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("env", env));
        for (PropertySource<?> source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
            sources.addLast(source);
        }
        return new PropertySourcesPropertyResolver(sources);
    }

    private static final String[] KEYSTORE = {"spring.ssl.bundle.jks.smartkin-security.keystore.location"};
    private static final String[] TRUSTSTORES = {"spring.ssl.bundle.jks.smartkin-security.truststore.location",
            "spring.cloud.vault.ssl.trust-store", "spring.cloud.config.tls.trust-store", "eureka.client.tls.trust-store"};

    @Test
    void defaultToTheClasspathNames() throws IOException {
        var resolver = resolver(Map.of("SSL_KEYSTORE_LOCATION", "smartkin.jks", "SSL_TRUSTSTORE_LOCATION", "umdc-truststore.jks"));
        for (String key : KEYSTORE) {
            assertEquals("classpath:smartkin.jks", resolver.getProperty(key), key);
        }
        for (String key : TRUSTSTORES) {
            assertEquals("classpath:umdc-truststore.jks", resolver.getProperty(key), key);
        }
    }

    @Test
    void anExplicitUrlWins() throws IOException {
        var resolver = resolver(Map.of("SSL_KEYSTORE_LOCATION", "smartkin.jks", "SSL_TRUSTSTORE_LOCATION", "umdc-truststore.jks",
                "SSL_KEYSTORE_URL", "file:/certs/smartkin.jks", "SSL_TRUSTSTORE_URL", "file:/certs/umdc-truststore.jks"));
        for (String key : KEYSTORE) {
            assertEquals("file:/certs/smartkin.jks", resolver.getProperty(key), key);
        }
        for (String key : TRUSTSTORES) {
            assertEquals("file:/certs/umdc-truststore.jks", resolver.getProperty(key), key);
        }
    }
}
