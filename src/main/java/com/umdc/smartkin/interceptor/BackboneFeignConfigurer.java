package com.umdc.smartkin.interceptor;

import com.umdc.security.properties.AuthProperties;
import feign.RequestInterceptor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.cloud.openfeign.security.OAuth2AccessTokenInterceptor.BEARER;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Configures a Feign client for the Backbone service.
 * This class extends the {@link BackendFeignConfigurer} to inherit and override
 * necessary configurations for the Backbone service.
 *
 * The BackboneFeignConfigurer sets up a specific {@link RequestInterceptor} bean
 * that adds authorization headers and other configurations required for requests
 * to the Backbone service.
 *
 * The configuration relies on {@link AuthProperties} to locate the client configuration
 * for the Backbone service by matching its ID with the constant BACKBONE_ID.
 */
public class BackboneFeignConfigurer extends BackendFeignConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackboneFeignConfigurer.class);

    private static final String MANAGED_CLIENT_TOKEN = "/api/v1/managed-clients/token";
    private static final long RENEW_BEFORE_EXPIRY_SECONDS = 60;

    @Value("${umdc.logging.trace.enabled}")
    private boolean isTraceEnabled;

    @Value("${umdc.clients.backbone.url}")
    private String backboneUrl;

    @Value("${umdc.clients.backbone.scopes}")
    private List<String> scopes;

    @Value("${umdc.clients.backbone.registration.client-id:}")
    private String registrationClientId;

    @Value("${umdc.clients.backbone.registration.client-secret:}")
    private String registrationClientSecret;

    private final Object tokenLock = new Object();
    private String cachedToken;
    private Instant cachedUntil = Instant.EPOCH;

    /**
     * Constructs an instance of {@code BackboneFeignConfigurer}.
     * This constructor initializes the configuration for the Backbone service
     * by identifying the appropriate client properties using the provided {@code AuthProperties}.
     * It filters the list of clients within {@code AuthProperties} to find the one
     * matching the constant {@code BACKBONE_ID}, and passes it to the parent class
     * {@link BackendFeignConfigurer}.
     *
     * @param authProperties the authentication properties containing client configurations
     *                        to locate the Backbone client configuration
     */
    public BackboneFeignConfigurer(AuthProperties authProperties) {
        super(authProperties.getClients().stream()
                .filter(authProperties1 -> authProperties1.getId()
                        .equalsIgnoreCase(BACKBONE_ID)).findFirst().orElse(null));
    }

    /**
     * Configures and provides a custom request interceptor bean named {@code backboneFeignClientInterceptor}.
     * This method overrides the parent class implementation to tailor the Feign client's behavior for
     * the Backbone service. It uses the {@link #createInterceptor()} method to construct a {@link RequestInterceptor}
     * that adds authorization headers and handles other necessary configurations for requests.
     *
     * @return a {@link RequestInterceptor} that customizes the Feign client's request behavior, including
     *         adding authorization headers and enabling optional request tracing for debugging purposes.
     */
    @Bean("backboneFeignClientInterceptor")
    @Override
    RequestInterceptor requestInterceptor() {
        return template -> {
            try {
                template.header(AUTHORIZATION, BEARER.concat(" ").concat(token()));
                if (isTraceEnabled) {
                    logHeaders(template.headers());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * Gets the M2M token of backbone-rest from its managed-client endpoint ({@code POST /api/v1/managed-clients/token}, the way
     * zerobound-rest does): the client id, secret and scope of the {@code backbone} client, answered as {@code accessToken}. The
     * old Keycloak-style form post to {@code AUTH_SERVER_URI} is gone from the environment. The token is kept until a minute
     * before it expires.
     *
     * @return the access token
     * @throws Exception when backbone does not answer a token
     */
    private String token() throws Exception {
        synchronized (tokenLock) {
            return currentToken();
        }
    }

    private String currentToken() throws Exception {
        if (Objects.nonNull(cachedToken) && Instant.now().isBefore(cachedUntil)) {
            return cachedToken;
        }
        requireValid();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("clientId", registrationClientId, "clientSecret", registrationClientSecret,
                "scopes", scopes);
        Map<?, ?> response;
        try {
            response = restTemplate().postForObject(backboneUrl + MANAGED_CLIENT_TOKEN, new HttpEntity<>(body, headers), Map.class);
        } catch (HttpStatusCodeException e) {
            throw new Exception("Backbone refused the token request for client " + registrationClientId + " scopes "
                    + scopes + ": " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        }
        Object token = Objects.isNull(response) ? null : response.get("accessToken");
        if (!(token instanceof String accessToken) || accessToken.isBlank()) {
            throw new Exception("Backbone answered no access token");
        }
        cachedToken = accessToken;
        cachedUntil = expiry(accessToken);
        return accessToken;
    }

    /// Backbone validates the body (`clientId` a UUID, `clientSecret` and every scope not blank) and answers a bare 400 without saying which
    /// field failed, so the same rules are checked here to name the environment variable at fault.
    private void requireValid() throws Exception {
        if (Objects.isNull(scopes) || scopes.isEmpty() || scopes.stream().anyMatch(String::isBlank)) {
            throw new Exception("BACKBONE_M2M_SCOPES is empty: it must list scopes of the managed client, e.g. users:manage");
        }
        try {
            UUID.fromString(String.valueOf(registrationClientId));
        } catch (IllegalArgumentException e) {
            throw new Exception("BACKBONE_REGISTRATION_CLIENT_ID must be the UUID of the managed client (scope users:manage) in backbone-rest, found '" + registrationClientId + "'", e);
        }
        if (Objects.isNull(registrationClientSecret) || registrationClientSecret.isBlank()) {
            throw new Exception("BACKBONE_REGISTRATION_CLIENT_SECRET is empty");
        }
    }

    private static void logHeaders(Map<String, Collection<String>> headers) {
        LOGGER.info("Headers key/value :::::");
        headers.forEach((key, value) -> LOGGER.info("KEY: {}, VALUE: {} :::::", key, value));
    }

    private static Instant expiry(String jwt) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
            return Instant.ofEpochSecond(new JSONObject(payload).getLong("exp") - RENEW_BEFORE_EXPIRY_SECONDS);
        } catch (RuntimeException e) {
            return Instant.EPOCH;
        }
    }
}
