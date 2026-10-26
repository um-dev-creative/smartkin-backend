package com.umdc.smartkin.kafka.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaSecurityPropertiesTest {

    @TempDir
    Path folder;

    @Configuration
    @EnableConfigurationProperties(KafkaSecurityProperties.class)
    static class Binding {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Binding.class);

    private static KafkaSecurityProperties.Ssl ssl(String truststore, String keystore, String cert, String key) {
        return new KafkaSecurityProperties.Ssl("PEM", truststore, "", "PEM", keystore, "", "", cert, key, "https");
    }

    private static KafkaSecurityProperties.Sasl sasl() {
        return new KafkaSecurityProperties.Sasl("SCRAM-SHA-256", "");
    }

    @Test
    void plaintextIsTheDefaultAndAddsNothingToTheClient() {
        runner.run(context -> {
            var properties = context.getBean(KafkaSecurityProperties.class);
            assertEquals("PLAINTEXT", properties.protocol());
            assertEquals(java.util.Map.of(), properties.clientProperties());
        });
        assertEquals(java.util.Map.of(), new KafkaSecurityProperties("plaintext", sasl(), ssl("", "", "", "")).clientProperties());
        assertEquals(java.util.Map.of(), new KafkaSecurityProperties("", sasl(), ssl("", "", "", "")).clientProperties());
        assertEquals(java.util.Map.of(), new KafkaSecurityProperties(null, sasl(), ssl("", "", "", "")).clientProperties());
    }

    @Test
    void sslWithOneKeystoreFileAndATruststore() {
        var client = new KafkaSecurityProperties("ssl", sasl(), ssl("/k/ca.pem", "/k/client.pem", "", "")).clientProperties();

        assertEquals("SSL", client.get("security.protocol"));
        assertEquals("PEM", client.get("ssl.truststore.type"));
        assertEquals("/k/ca.pem", client.get("ssl.truststore.location"));
        assertEquals("PEM", client.get("ssl.keystore.type"));
        assertEquals("/k/client.pem", client.get("ssl.keystore.location"));
        assertEquals("https", client.get("ssl.endpoint.identification.algorithm"));
        assertFalse(client.containsKey("sasl.mechanism"));
        assertFalse(client.containsKey("ssl.keystore.password"));
        assertFalse(client.containsKey("ssl.key.password"));
    }

    @Test
    void theFileSchemeIsAcceptedInAllItsForms() {
        var client = new KafkaSecurityProperties("SSL", sasl(), ssl("file:/k/ca.pem", "file:///k/client.pem", "", "")).clientProperties();

        assertEquals("/k/ca.pem", client.get("ssl.truststore.location"));
        assertEquals("/k/client.pem", client.get("ssl.keystore.location"));
    }

    @Test
    void pkcs12AndPasswordsArePassedOnlyWhenSet() {
        var tls = new KafkaSecurityProperties.Ssl("JKS", "/k/trust.jks", "tp", "PKCS12", "/k/client.p12", "kp", "keypw", "", "", "");

        var client = new KafkaSecurityProperties("SSL", sasl(), tls).clientProperties();

        assertEquals("JKS", client.get("ssl.truststore.type"));
        assertEquals("tp", client.get("ssl.truststore.password"));
        assertEquals("PKCS12", client.get("ssl.keystore.type"));
        assertEquals("kp", client.get("ssl.keystore.password"));
        assertEquals("keypw", client.get("ssl.key.password"));
        assertEquals("", client.get("ssl.endpoint.identification.algorithm"), "an empty value turns the host name check off");
    }

    @Test
    void aClientCertificateAndKeyInTwoFilesAreHandedToTheClientAsPemText() throws IOException {
        Path cert = Files.writeString(folder.resolve("service.cert"), "-----BEGIN CERTIFICATE-----\nAAA\n-----END CERTIFICATE-----\n");
        Path key = Files.writeString(folder.resolve("service.key"), "-----BEGIN PRIVATE KEY-----\nBBB\n-----END PRIVATE KEY-----\n");

        var client = new KafkaSecurityProperties("SSL", sasl(), ssl("/k/ca.pem", "", "file:" + cert, key.toString())).clientProperties();

        assertEquals("PEM", client.get("ssl.keystore.type"));
        assertTrue(client.get("ssl.keystore.certificate.chain").toString().contains("BEGIN CERTIFICATE"));
        assertTrue(client.get("ssl.keystore.key").toString().contains("BEGIN PRIVATE KEY"));
        assertFalse(client.containsKey("ssl.keystore.location"));
    }

    @Test
    void aSingleKeystoreFileWinsOverTheTwoFileForm() {
        var client = new KafkaSecurityProperties("SSL", sasl(), ssl("", "/k/client.pem", "/nope/c", "/nope/k")).clientProperties();

        assertEquals("/k/client.pem", client.get("ssl.keystore.location"));
        assertFalse(client.containsKey("ssl.keystore.certificate.chain"));
    }

    @Test
    void anUnreadableFileFailsFastWithoutLeakingAnything() throws IOException {
        Path key = Files.writeString(folder.resolve("service.key"), "TOP-SECRET-KEY-MATERIAL");

        var missing = assertThrows(IllegalStateException.class,
                () -> new KafkaSecurityProperties("SSL", sasl(), ssl("", "", folder.resolve("absent.cert").toString(), key.toString())).clientProperties());
        var half = assertThrows(IllegalStateException.class,
                () -> new KafkaSecurityProperties("SSL", sasl(), ssl("", "", folder.resolve("absent.cert").toString(), "")).clientProperties());

        assertTrue(missing.getMessage().contains("keystore-certificate-location"), missing.getMessage());
        assertTrue(missing.getMessage().contains("absent.cert"), missing.getMessage());
        assertFalse(missing.getMessage().contains("TOP-SECRET"));
        assertTrue(half.getMessage().contains("both needed") || half.getMessage().contains("Cannot read"), half.getMessage());
    }

    @Test
    void saslSslAddsTheMechanismAndTheLoginLine() {
        var scram = new KafkaSecurityProperties.Sasl("SCRAM-SHA-512",
                "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"u\" password=\"p\";");

        var client = new KafkaSecurityProperties("SASL_SSL", scram, ssl("/k/ca.pem", "", "", "")).clientProperties();

        assertEquals("SASL_SSL", client.get("security.protocol"));
        assertEquals("SCRAM-SHA-512", client.get("sasl.mechanism"));
        assertTrue(client.get("sasl.jaas.config").toString().contains("ScramLoginModule"));
        assertEquals("/k/ca.pem", client.get("ssl.truststore.location"));
        assertFalse(client.containsKey("ssl.keystore.type"));
    }

    @Test
    void saslWithoutTlsHasNoSslSettings() {
        var client = new KafkaSecurityProperties("SASL_PLAINTEXT", new KafkaSecurityProperties.Sasl("PLAIN", ""), ssl("/k/ca.pem", "/k/c.pem", "", ""))
                .clientProperties();

        assertEquals("SASL_PLAINTEXT", client.get("security.protocol"));
        assertEquals("PLAIN", client.get("sasl.mechanism"));
        assertFalse(client.containsKey("sasl.jaas.config"));
        assertFalse(client.keySet().stream().anyMatch(name -> name.startsWith("ssl.")));
    }

    @Test
    void anUnknownProtocolIsRejectedWithTheValidOnes() {
        var error = assertThrows(IllegalArgumentException.class, () -> new KafkaSecurityProperties("TLS", sasl(), ssl("", "", "", "")).clientProperties());

        assertTrue(error.getMessage().contains("'TLS'") && error.getMessage().contains("SASL_SSL"), error.getMessage());
    }

    @Test
    void secretsNeverAppearInToString() {
        var tls = new KafkaSecurityProperties.Ssl("PEM", "/k/ca.pem", "TRUSTPW", "PKCS12", "/k/c.p12", "STOREPW", "KEYPW", "", "", "https");
        var properties = new KafkaSecurityProperties("SASL_SSL",
                new KafkaSecurityProperties.Sasl("SCRAM-SHA-256", "ScramLoginModule required username=\"u\" password=\"SASLPW\";"), tls);

        String text = properties.toString();

        for (String secret : new String[] {"TRUSTPW", "STOREPW", "KEYPW", "SASLPW"}) {
            assertFalse(text.contains(secret), secret + " leaked in " + text);
        }
        assertTrue(text.contains("/k/ca.pem") && text.contains("SCRAM-SHA-256"));
    }

    @Test
    void bindsFromTheEnvironmentNamesTheYamlMaps() {
        runner.withPropertyValues(
                "umdc.kafka.security.protocol=SASL_SSL",
                "umdc.kafka.security.sasl.jaas-config=login",
                "umdc.kafka.security.ssl.truststore-location=file:/k/ca.pem",
                "umdc.kafka.security.ssl.keystore-certificate-location=/k/service.cert",
                "umdc.kafka.security.ssl.keystore-key-location=/k/service.key",
                "umdc.kafka.security.ssl.endpoint-identification-algorithm=").run(context -> {
            var properties = context.getBean(KafkaSecurityProperties.class);
            assertEquals("SASL_SSL", properties.protocol());
            assertEquals("login", properties.sasl().jaasConfig());
            assertEquals("SCRAM-SHA-256", properties.sasl().mechanism());
            assertEquals("file:/k/ca.pem", properties.ssl().truststoreLocation());
            assertEquals("/k/service.cert", properties.ssl().keystoreCertificateLocation());
            assertEquals("/k/service.key", properties.ssl().keystoreKeyLocation());
            assertEquals("", properties.ssl().endpointIdentificationAlgorithm());
            assertEquals("PEM", properties.ssl().truststoreType());
        });
    }
}
