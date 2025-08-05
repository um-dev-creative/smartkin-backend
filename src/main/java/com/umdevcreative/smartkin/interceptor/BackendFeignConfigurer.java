package com.umdevcreative.smartkin.interceptor;

import com.prx.commons.general.pojo.UserSession;
import com.prx.commons.general.to.TokenResponse;
import com.prx.security.properties.ClientProperties;
import feign.RequestInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.cloud.openfeign.security.OAuth2AccessTokenInterceptor.BEARER;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

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
public abstract class BackendFeignConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendFeignConfigurer.class);
    protected static final String BACKBONE_ID = "backbone";
    protected static final String MERCURY_ID = "mercury";

    @Value("${prx.logging.trace.enabled}")
    private boolean isTraceEnabled;

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
        this.clientProperties = clientProperties;
    }

    abstract RequestInterceptor requestInterceptor();

    /**
     * Creates a {@link RequestInterceptor} that customizes the behavior of the Feign client by adding
     * an authorization header and, if enabled, logging the request headers for debugging purposes.
     *
     * The interceptor retrieves a token based on the provided {@code ClientProperties} and appends it
     * as a Bearer Token in the authorization header of the request. If trace logging is enabled,
     * the method logs all headers from the outgoing request.
     *
     * @return a {@link RequestInterceptor} that adds an authorization header and logs request headers
     *         when tracing is enabled.
     */
    protected RequestInterceptor createInterceptor() {
        return template -> {
            try {
                assert clientProperties != null;
                String token = getToken(clientProperties);
                template.header(AUTHORIZATION, BEARER.concat(" ").concat(token));
                if (isTraceEnabled) {
                    logHeaders(template.headers());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * Retrieves an authentication token by making a POST request to the specified server URI
     * using the provided client properties.
     *
     * @param clientProperties the client properties containing credentials and configuration
     *                          required to obtain the token. Must include grant type, client ID,
     *                          username, password, client secret, and redirect URI.
     * @return the authentication token as a {@code String}.
     * @throws Exception if the token could not be retrieved due to an error during the server interaction.
     */
    protected String getToken(ClientProperties clientProperties) throws Exception {
        RestTemplate client = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("grant_type", clientProperties.getAuthorizationGrantType());
        parameters.add("client_id", clientProperties.getClientId());
        parameters.add("username", clientProperties.getUsername());
        parameters.add("password", clientProperties.getPassword());
        parameters.add("client_secret", clientProperties.getClientSecret());

        var response = client.postForObject(clientProperties.getRedirectUri(), new HttpEntity<>(parameters, headers), TokenResponse.class);
        if (Objects.isNull(response)) {
            LOGGER.error("Error occurred while connecting with the Manager authenticator");
            throw new Exception("Error occurred while connecting with the Manager authenticator");
        }
        return create(response, UUID.randomUUID()).token();
    }

    private void logHeaders(Map<String, Collection<String>> headers) {
        LOGGER.info("Headers key/value :::::");
        headers.forEach((key, value) -> LOGGER.info("KEY: {}, VALUE: {} :::::", key, value));
    }

    /**
     * Creates a new {@link UserSession} instance using the provided token response and UUID.
     *
     * @param tokenResponse the {@link TokenResponse} containing the access token used to establish the session
     * @param id the unique identifier for the session
     * @return a new {@link UserSession} instance initialized with the provided UUID and access token
     */
    protected static UserSession create(TokenResponse tokenResponse, UUID id) {
        return new UserSession(id, null, tokenResponse.accessToken());
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
