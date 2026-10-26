package com.umdc.smartkin.client;

import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.client.mercury.MercuryClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeignClientUrlConfigTest {

    private static final String BACKBONE_DEFAULT = "https://prx-qa.backbone.tst/backbone";
    private static final String MERCURY_DEFAULT = "https://prx-qa.backbone.tst/mercury";

    private static PropertySourcesPropertyResolver resolver(Map<String, Object> overrides) throws IOException {
        var sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("overrides", overrides));
        for (PropertySource<?> source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
            sources.addLast(source);
        }
        return new PropertySourcesPropertyResolver(sources);
    }

    private static String urlOf(Class<?> client) {
        return client.getAnnotation(FeignClient.class).url();
    }

    @Test
    void clientsReadTheirBaseUrlFromProperties() {
        assertEquals("${umdc.clients.backbone.url}", urlOf(BackboneClient.class));
        assertEquals("${umdc.clients.mercury.url}", urlOf(MercuryClient.class));
    }

    @Test
    void defaultsKeepThePreviousQaUrls() throws IOException {
        var resolver = resolver(Map.of());

        assertEquals(BACKBONE_DEFAULT, resolver.resolvePlaceholders(urlOf(BackboneClient.class)));
        assertEquals(MERCURY_DEFAULT, resolver.resolvePlaceholders(urlOf(MercuryClient.class)));
    }

    @Test
    void environmentVariablesOverrideTheDefaults() throws IOException {
        var resolver = resolver(Map.of("BACKBONE_URL", "https://backbone.example/api", "MERCURY_URL", "https://mercury.example/api"));

        assertEquals("https://backbone.example/api", resolver.resolvePlaceholders(urlOf(BackboneClient.class)));
        assertEquals("https://mercury.example/api", resolver.resolvePlaceholders(urlOf(MercuryClient.class)));
    }
}
