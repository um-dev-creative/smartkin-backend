package com.umdc.smartkin.config;

import com.umdc.security.jwt.JwtConfigProperties;
import com.umdc.security.properties.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// application.yml must use the property names the com.umdc libraries bind (`umdc.*`). With the old `prx.*` names nothing binds, the
/// security library's `AuthProperties.getClients()` is null and the context dies at start; no other test starts the whole context.
class LibraryPropertiesBindingTest {

    private StandardEnvironment environmentOverApplicationYml() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        var environment = new StandardEnvironment();
        MutablePropertySources all = environment.getPropertySources();
        // the values Vault / the environment supply at run time
        all.addFirst(new MapPropertySource("env", Map.ofEntries(
                Map.entry("APP_TOKEN_SECRET", "secret-0123456789-0123456789-0123456789"),
                Map.entry("APP_TOKEN_EXPIRATION", "3600000"),
                Map.entry("AUTH_ROLE_ID", "role"),
                Map.entry("AUTH_SERVER_URI", "https://auth"),
                Map.entry("AUTH_TOKEN_URI", "/token"),
                Map.entry("BACKBONE_GRANT_TYPE", "password"),
                Map.entry("BACKBONE_SCOPE", "openid"),
                Map.entry("BACKBONE_CLIENT_ID", "bb-id"),
                Map.entry("BACKBONE_CLIENT_SECRET", "bb-secret"),
                Map.entry("BACKBONE_USERNAME", "bb-user"),
                Map.entry("BACKBONE_PASSWORD", "bb-pass"),
                Map.entry("MERCURY_GRANT_TYPE", "password"),
                Map.entry("MERCURY_SCOPE", "openid"),
                Map.entry("MERCURY_CLIENT_ID", "m-id"),
                Map.entry("MERCURY_CLIENT_SECRET", "m-secret"),
                Map.entry("MERCURY_USERNAME", "m-user"),
                Map.entry("MERCURY_PASSWORD", "m-pass"),
                Map.entry("LOGGING_TRACE_ENABLED", "false"))));
        sources.forEach(all::addLast);
        return environment;
    }

    private Binder binderOverApplicationYml() throws IOException {
        var environment = environmentOverApplicationYml();
        return new Binder(ConfigurationPropertySources.get(environment), new PropertySourcesPlaceholdersResolver(environment));
    }

    @Test
    void theOutboundClientsBindToTheSecurityLibrary() throws IOException {
        AuthProperties auth = binderOverApplicationYml().bind("umdc.security.auth", AuthProperties.class).orElse(null);

        assertNotNull(auth);
        assertNotNull(auth.getClients());
        assertEquals(List.of("backbone", "mercury"), auth.getClients().stream().map(c -> c.getId()).toList());
        var backbone = auth.getClients().get(0);
        assertEquals("password", backbone.getAuthorizationGrantType());
        assertEquals("bb-id", backbone.getClientId());
        assertEquals("https://auth/token", backbone.getRedirectUri());
    }

    @Test
    void theJwtSettingsBindToTheSecurityLibrary() throws IOException {
        JwtConfigProperties jwt = binderOverApplicationYml().bind("umdc.security.jwt", JwtConfigProperties.class).orElse(null);

        assertNotNull(jwt);
        assertEquals("secret-0123456789-0123456789-0123456789", jwt.getSecret());
        assertEquals(3_600_000L, jwt.getExpirationMs());
    }

    @Test
    void theLibrariesFindTheirPlaceholders() throws IOException {
        // the libraries read these with @Value("${...}"), which keeps the camelCase of the YAML key
        var environment = environmentOverApplicationYml();

        assertEquals("/api/v1", environment.getProperty("umdc.api.endpoint"));
        assertEquals("https://prx-qa.backbone.tst/backbone", environment.getProperty("umdc.backbone.base-url"));
        assertEquals("role", environment.getProperty("umdc.management.clientRoles"));
        assertEquals("false", environment.getProperty("umdc.logging.trace.enabled"));
    }
}
