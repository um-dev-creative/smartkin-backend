package com.umdc.smartkin.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/// How the producer and the consumer authenticate to the brokers (`umdc.kafka.security.*`).
///
/// The default is `PLAINTEXT`, which adds nothing to the client configuration (a local broker, the Testcontainers tests). A managed
/// cluster such as Aiven needs `SSL` (client certificate) or `SASL_SSL` (user and password): the settings below become the Kafka client
/// properties of both factories through [#clientProperties()]. They sit under `umdc.kafka.security` like the rest of the service's
/// settings, and application.yml reads them from `BOOTSTRAP_SECURITY_PROTOCOL`, `BOOTSTRAP_SSL_*` and `BOOTSTRAP_SASL_*`.
///
/// @param protocol `PLAINTEXT`, `SSL`, `SASL_PLAINTEXT` or `SASL_SSL`
/// @param sasl     SASL settings, used when the protocol starts with `SASL`
/// @param ssl      TLS settings, used when the protocol ends with `SSL`
@ConfigurationProperties("umdc.kafka.security")
public record KafkaSecurityProperties(
        @DefaultValue("PLAINTEXT") String protocol,
        @DefaultValue Sasl sasl,
        @DefaultValue Ssl ssl) {

    private static final Set<String> PROTOCOLS = Set.of("PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL");

    /// SASL settings.
    ///
    /// @param mechanism  `SCRAM-SHA-256` (Aiven), `SCRAM-SHA-512` or `PLAIN`
    /// @param jaasConfig the `sasl.jaas.config` line, for example `org.apache.kafka.common.security.scram.ScramLoginModule required username="u" password="p";`
    public record Sasl(@DefaultValue("SCRAM-SHA-256") String mechanism, @DefaultValue("") String jaasConfig) {

        @Override
        public String toString() {
            return "Sasl[mechanism=" + mechanism + ", jaasConfig=" + (jaasConfig.isBlank() ? "" : "***") + "]";
        }
    }

    /// TLS settings. Files are read from the file system (`file:` is optional), never from the classpath, because a GraalVM binary does
    /// not embed them and a private key does not belong in an image.
    ///
    /// @param truststoreType                `PEM` (Aiven's `ca.pem`), `PKCS12` or `JKS`
    /// @param truststoreLocation            the CA that signed the brokers' certificates; empty = the JVM's default trust
    /// @param truststorePassword            only for `PKCS12` and `JKS`
    /// @param keystoreType                  `PEM`, `PKCS12` or `JKS`
    /// @param keystoreLocation              one file with the client key and certificate (a PEM with both, or a PKCS12/JKS store)
    /// @param keystorePassword              only for `PKCS12` and `JKS`
    /// @param keyPassword                   password of an encrypted private key
    /// @param keystoreCertificateLocation   alternative to `keystoreLocation`: the certificate file as Aiven delivers it (`service.cert`)
    /// @param keystoreKeyLocation           ... and its key file (`service.key`); both are read and handed to the client as PEM text
    /// @param endpointIdentificationAlgorithm `https` checks the brokers' host name against their certificate; empty turns the check off
    public record Ssl(
            @DefaultValue("PEM") String truststoreType,
            @DefaultValue("") String truststoreLocation,
            @DefaultValue("") String truststorePassword,
            @DefaultValue("PEM") String keystoreType,
            @DefaultValue("") String keystoreLocation,
            @DefaultValue("") String keystorePassword,
            @DefaultValue("") String keyPassword,
            @DefaultValue("") String keystoreCertificateLocation,
            @DefaultValue("") String keystoreKeyLocation,
            @DefaultValue("https") String endpointIdentificationAlgorithm) {

        @Override
        public String toString() {
            return "Ssl[truststoreType=" + truststoreType + ", truststoreLocation=" + truststoreLocation
                    + ", keystoreType=" + keystoreType + ", keystoreLocation=" + keystoreLocation
                    + ", keystoreCertificateLocation=" + keystoreCertificateLocation + ", keystoreKeyLocation=" + keystoreKeyLocation
                    + ", endpointIdentificationAlgorithm=" + endpointIdentificationAlgorithm + ", passwords=***]";
        }
    }

    /// The Kafka client properties for these settings: empty for `PLAINTEXT`, so nothing changes for a deployment that does not set them.
    ///
    /// @return properties to add to a producer or consumer configuration
    /// @throws IllegalArgumentException when the protocol is not one Kafka knows
    /// @throws IllegalStateException    when a certificate or key file cannot be read (the message never carries the file's content)
    public Map<String, Object> clientProperties() {
        String selected = protocol == null || protocol.isBlank() ? "PLAINTEXT" : protocol.trim().toUpperCase(Locale.ROOT);
        if (!PROTOCOLS.contains(selected)) {
            throw new IllegalArgumentException("Unsupported umdc.kafka.security.protocol '" + protocol + "' (use one of " + new TreeSet<>(PROTOCOLS) + ")");
        }
        Map<String, Object> client = new HashMap<>();
        if ("PLAINTEXT".equals(selected)) {
            return client;
        }
        client.put("security.protocol", selected);
        if (selected.startsWith("SASL")) {
            client.put("sasl.mechanism", sasl.mechanism());
            putIfPresent(client, "sasl.jaas.config", sasl.jaasConfig());
        }
        if (selected.endsWith("SSL")) {
            client.put("ssl.endpoint.identification.algorithm", ssl.endpointIdentificationAlgorithm());
            if (!ssl.truststoreLocation().isBlank()) {
                client.put("ssl.truststore.type", ssl.truststoreType());
                client.put("ssl.truststore.location", path(ssl.truststoreLocation()));
                putIfPresent(client, "ssl.truststore.password", ssl.truststorePassword());
            }
            if (!ssl.keystoreLocation().isBlank()) {
                client.put("ssl.keystore.type", ssl.keystoreType());
                client.put("ssl.keystore.location", path(ssl.keystoreLocation()));
                putIfPresent(client, "ssl.keystore.password", ssl.keystorePassword());
            } else if (!ssl.keystoreCertificateLocation().isBlank() || !ssl.keystoreKeyLocation().isBlank()) {
                client.put("ssl.keystore.type", "PEM");
                client.put("ssl.keystore.certificate.chain", read("keystore-certificate-location", ssl.keystoreCertificateLocation()));
                client.put("ssl.keystore.key", read("keystore-key-location", ssl.keystoreKeyLocation()));
            }
            putIfPresent(client, "ssl.key.password", ssl.keyPassword());
        }
        return client;
    }

    private static void putIfPresent(Map<String, Object> client, String key, String value) {
        if (value != null && !value.isBlank()) {
            client.put(key, value);
        }
    }

    /// `file:/a/b`, `file:///a/b` and `/a/b` all name the same file; Kafka wants the plain path.
    private static String path(String location) {
        if (!location.startsWith("file:")) {
            return location;
        }
        String plain = location.substring("file:".length());
        return plain.startsWith("//") ? plain.substring(2) : plain;
    }

    private static String read(String property, String location) {
        if (location.isBlank()) {
            throw new IllegalStateException("umdc.kafka.security.ssl." + property + " is empty: the certificate and the key files are both needed");
        }
        try {
            return Files.readString(Path.of(path(location)));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot read the Kafka TLS file of umdc.kafka.security.ssl." + property + ": " + location
                    + " (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    @Override
    public String toString() {
        return "KafkaSecurityProperties[protocol=" + protocol + ", sasl=" + sasl + ", ssl=" + ssl + "]";
    }
}
