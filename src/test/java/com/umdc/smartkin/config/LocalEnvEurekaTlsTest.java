package com.umdc.smartkin.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The Config Server builds the Eureka client TLS stores as `classpath:${SSL_JKS_SRMN_*_LOCATION}` and Vault stores those locations as
/// `file:certs/smartkin/...`; together they give `classpath:file:certs/...` and the application stops at start ("KeyStore not exists").
/// `config/local.env` must therefore override them with the names of the jar's own stores (a name without a scheme).
class LocalEnvEurekaTlsTest {

    private static Properties localEnv() throws IOException {
        var props = new Properties();
        props.load(new StringReader(Files.readString(Path.of("config/local.env"))));
        return props;
    }

    @Test
    void theEurekaStoresAreTheJarsOwnClasspathNames() throws IOException {
        var env = localEnv();
        assertEquals(env.getProperty("SSL_TRUSTSTORE_LOCATION"), env.getProperty("SSL_JKS_SRMN_TRUSTSTORE_LOCATION"));
        assertEquals(env.getProperty("SSL_KEYSTORE_LOCATION"), env.getProperty("SSL_JKS_SRMN_KEYSTORE_LOCATION"));
        for (String key : new String[]{"SSL_JKS_SRMN_TRUSTSTORE_LOCATION", "SSL_JKS_SRMN_KEYSTORE_LOCATION"}) {
            String value = env.getProperty(key);
            assertNotNull(value, key + " is missing from config/local.env");
            assertFalse(value.contains(":"), key + " must be a classpath name without a scheme: " + value);
        }
    }

    @Test
    void theJvmImageCarriesTheSameStoreNamesAsDefaults() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        assertTrue(dockerfile.contains("SSL_JKS_SRMN_TRUSTSTORE_LOCATION=umdc-truststore.jks"));
        assertTrue(dockerfile.contains("SSL_JKS_SRMN_KEYSTORE_LOCATION=smartkin.jks"));
        // Vault's passwords belong to its own stores and do not open the jar's: the placeholder of the dev stores goes with the names
        assertTrue(dockerfile.contains("SSL_JKS_SRMN_TRUSTSTORE_PASSWORD=changeit"));
        assertTrue(dockerfile.contains("SSL_JKS_SRMN_KEYSTORE_PASSWORD=changeit"));
    }

    @Test
    void theEurekaStoresHaveTypeAndPassword() throws IOException {
        var env = localEnv();
        for (String key : new String[]{"SSL_JKS_SRMN_TRUSTSTORE_TYPE", "SSL_JKS_SRMN_TRUSTSTORE_PASSWORD", "SSL_JKS_SRMN_KEYSTORE_TYPE",
                "SSL_JKS_SRMN_KEYSTORE_PASSWORD"}) {
            assertNotNull(env.getProperty(key), key + " is missing from config/local.env");
        }
    }
}
