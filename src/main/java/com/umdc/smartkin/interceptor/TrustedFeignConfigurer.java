package com.umdc.smartkin.interceptor;

import feign.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;

import java.util.Objects;

/// Feign configuration that only makes a client trust the internal CA of the `*.umdc-qa.tst` services: it adds no interceptor, so no
/// `Authorization` header is sent. Feign's default client trusts only the JVM trust store, which lacks that CA (`PKIX path building
/// failed`); this one trusts the `smartkin-security` SSL bundle. [BackendFeignConfigurer] extends it, so the clients that carry an OAuth
/// bearer get the same transport, and the token clients that must not carry one (`BackboneManagedClient`, `MercuryAuthClient`) name it
/// as their `configuration`. Not a `@Configuration`: Spring Cloud OpenFeign applies it to the clients that name it, not to all of them.
public class TrustedFeignConfigurer {

    private static final String SSL_BUNDLE = "smartkin-security";

    /// The SSL bundles of the application. The services are behind nginx with the internal CA, which the JVM trust store does not hold;
    /// the `smartkin-security` bundle does.
    @Autowired(required = false)
    private SslBundles sslBundles;

    /// Builds the configuration; Spring fills `sslBundles`.
    public TrustedFeignConfigurer() {
        super();
    }

    /// The HTTP client of the Feign calls: it trusts the `smartkin-security` bundle; without bundles (plain unit tests) it falls back to
    /// the JVM defaults.
    ///
    /// @return the client Feign sends its requests with
    @Bean
    Client feignHttpClient() {
        if (Objects.isNull(sslBundles)) {
            return new Client.Default(null, null);
        }
        return new Client.Default(sslBundles().getBundle(SSL_BUNDLE).createSslContext().getSocketFactory(), null);
    }

    /// The application's SSL bundles, empty when there are none.
    ///
    /// @return the bundles, or `null` in plain unit tests
    protected final SslBundles sslBundles() {
        return sslBundles;
    }

    /// The name of the bundle that trusts the internal CA.
    ///
    /// @return `smartkin-security`
    protected static String sslBundleName() {
        return SSL_BUNDLE;
    }
}
