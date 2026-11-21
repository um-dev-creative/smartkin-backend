package com.umdc.smartkin.interceptor;

import com.umdc.security.properties.ClientProperties;
import feign.RequestInterceptor;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;


/**
 * An abstract class that provides a template for configuring Feign clients with custom behaviors
 * and request interceptors, particularly for authenticated requests to external services.
 *
 * This class facilitates customization of Feign client behavior by allowing subclasses to define
 * specific request interceptors through the {@link #requestInterceptor()} method. It provides a
 * common framework for managing client properties, generating authorization tokens, and logging
 * request headers for debugging purposes.
 *
 * Key functionalities include:
 * - Generating authentication tokens using client properties provided in the constructor.
 * - Setting up request interceptors that can add authorization headers dynamically.
 * - Logging outgoing request headers if trace logging is enabled.
 *
 * Subclasses are expected to override the {@link #requestInterceptor()} method to provide
 * their implementation of Feign-specific configuration.
 */
public abstract class BackendFeignConfigurer extends TrustedFeignConfigurer {

    protected static final String BACKBONE_ID = "backbone";
    protected static final String MERCURY_ID = "mercury";

    /**
     * Holds the configuration properties specific to the client for which this
     * Feign configurer is set up. These properties include client-specific
     * credentials, endpoints, and other related configuration details.
     *
     * The {@code ClientProperties} instance is used for constructing authorization
     * tokens, setting up request interceptors, and performing various client-related
     * operations within this Feign configurer.
     *
     * This variable is initialized via the constructor of {@code BackendFeignConfigurer}
     * or its subclasses and remains immutable throughout the lifecycle of the
     * containing instance.
     */
    private final ClientProperties clientProperties;

    /**
     * Constructs an instance of {@code BackendFeignConfigurer} with the specified client properties.
     * This constructor initializes the configuration needed for Feign communication by using
     * the provided {@link ClientProperties}.
     *
     * @param clientProperties the client properties used to configure and identify the Feign client
     */
    protected BackendFeignConfigurer(ClientProperties clientProperties) {
        super();
        this.clientProperties = clientProperties;
    }

    abstract RequestInterceptor requestInterceptor();

    /// Builds the client for the token endpoint. It trusts what the `smartkin-security` bundle trusts (the internal CA); without bundles
    /// (plain unit tests) it falls back to the JVM defaults.
    protected RestTemplate restTemplate() {
        if (Objects.isNull(sslBundles())) {
            return new RestTemplate();
        }
        return new RestTemplateBuilder().sslBundle(sslBundles().getBundle(sslBundleName())).build();
    }

    /**
     * Retrieves the client ID from the configured {@code ClientProperties}.
     *
     * @return the client ID as a {@code String}.
     */
    public final String getClientId() {
        return clientProperties.getClientId();
    }
    
}
