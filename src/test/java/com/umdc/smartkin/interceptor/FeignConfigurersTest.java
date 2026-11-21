package com.umdc.smartkin.interceptor;

import com.sun.net.httpserver.HttpServer;
import com.umdc.security.properties.AuthProperties;
import com.umdc.security.properties.ClientProperties;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the Feign interceptors against a local HTTP server that plays the OAuth token endpoint.
class FeignConfigurersTest {

    private static final String TOKEN_JSON = "{\"access_token\":\"abc123\",\"expires_in\":300,\"refresh_expires_in\":0,"
            + "\"refresh_token\":\"r\",\"token_type\":\"Bearer\",\"not-before-policy\":0,\"session_state\":\"s\",\"scope\":\"x\"}";

    private HttpServer server;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = TOKEN_JSON;
    private final AtomicReference<String> receivedBackboneBody = new AtomicReference<>();
    private volatile int backboneStatus = 200;
    private volatile String backboneResponse = "{\"accessToken\":\"abc123\",\"tokenType\":\"Bearer\",\"expiresIn\":3600}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.createContext("/api/v1/managed-clients/token", exchange -> {
            receivedBackboneBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var bytes = backboneResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(backboneStatus, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    private static final String BACKBONE_CLIENT_ID = "6f1c1c1e-3b52-4a43-9d57-2f4f3f1d9a10";

    private BackboneFeignConfigurer backboneConfigurer() {
        var configurer = new BackboneFeignConfigurer(authProperties());
        ReflectionTestUtils.setField(configurer, "backboneUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(configurer, "scopes", List.of("users:manage"));
        ReflectionTestUtils.setField(configurer, "registrationClientId", BACKBONE_CLIENT_ID);
        ReflectionTestUtils.setField(configurer, "registrationClientSecret", "secret");
        return configurer;
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ClientProperties client(String id) {
        var client = new ClientProperties();
        client.setId(id);
        client.setAuthorizationGrantType("password");
        client.setScope("openid");
        client.setClientId(id + "-client");
        client.setClientSecret("secret");
        client.setUsername("user");
        client.setPassword("pwd");
        client.setRedirectUri("http://127.0.0.1:" + server.getAddress().getPort() + "/token");
        return client;
    }

    private AuthProperties authProperties() {
        var properties = new AuthProperties();
        properties.setClients(List.of(client("backbone"), client("mercury")));
        return properties;
    }

    @Test
    void backboneInterceptorAddsTheBearerToken() {
        var configurer = backboneConfigurer();
        var template = new RequestTemplate();

        configurer.requestInterceptor().apply(template);

        assertEquals(List.of("Bearer abc123"), List.copyOf(template.headers().get("Authorization")));
        assertEquals("backbone-client", configurer.getClientId());
        assertTrue(receivedBackboneBody.get().contains("\"clientId\":\"" + BACKBONE_CLIENT_ID + "\""));
        assertTrue(receivedBackboneBody.get().contains("\"clientSecret\":\"secret\""));
        assertTrue(receivedBackboneBody.get().contains("\"scopes\":[\"users:manage\"]"));
    }

    @Test
    void mercuryInterceptorAsksNoTokenServerAndAddsNoBearer() {
        var configurer = new MercuryFeignConfigurer(authProperties());
        var template = new RequestTemplate();
        receivedBody.set(null);

        configurer.requestInterceptor().apply(template);

        assertEquals("mercury-client", configurer.getClientId());
        assertTrue(!template.headers().containsKey("Authorization"));
        assertEquals(null, receivedBody.get());
    }

    @Test
    void interceptorFailsWhenTheAuthenticatorAnswersWithoutBody() {
        backboneStatus = 200;
        backboneResponse = "{}";
        var configurer = backboneConfigurer();

        var failure = assertThrows(RuntimeException.class, () -> configurer.requestInterceptor().apply(new RequestTemplate()));

        assertTrue(failure.getCause().getMessage().contains("no access token"));
    }

    @Test
    void interceptorFailsWhenTheAuthenticatorIsDown() {
        var configurer = backboneConfigurer();
        server.stop(0);

        assertThrows(RuntimeException.class, () -> configurer.requestInterceptor().apply(new RequestTemplate()));
    }

    @Test
    void backboneKeepsTheTokenUntilItIsAboutToExpire() {
        var expiring = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"exp\":" + (java.time.Instant.now().getEpochSecond() + 3600) + "}").getBytes(StandardCharsets.UTF_8));
        backboneResponse = "{\"accessToken\":\"h." + expiring + ".s\"}";
        var configurer = backboneConfigurer();

        configurer.requestInterceptor().apply(new RequestTemplate());
        receivedBackboneBody.set(null);
        configurer.requestInterceptor().apply(new RequestTemplate());

        assertEquals(null, receivedBackboneBody.get());
    }

    @Test
    void backboneRefusesABlankAccessToken() {
        backboneResponse = "{\"accessToken\":\" \"}";
        var configurer = backboneConfigurer();

        var failure = assertThrows(RuntimeException.class, () -> configurer.requestInterceptor().apply(new RequestTemplate()));

        assertTrue(failure.getCause().getMessage().contains("no access token"));
    }

    @Test
    void backboneNamesTheClientIdThatIsNotAUuid() {
        var configurer = backboneConfigurer();
        ReflectionTestUtils.setField(configurer, "registrationClientId", "smartkin");

        var failure = assertThrows(RuntimeException.class, () -> configurer.requestInterceptor().apply(new RequestTemplate()));

        assertTrue(failure.getCause().getMessage().contains("BACKBONE_REGISTRATION_CLIENT_ID must be the UUID"));
    }

    @Test
    void backboneNamesAnEmptySecret() {
        var configurer = backboneConfigurer();
        ReflectionTestUtils.setField(configurer, "registrationClientSecret", " ");

        var failure = assertThrows(RuntimeException.class, () -> configurer.requestInterceptor().apply(new RequestTemplate()));

        assertTrue(failure.getCause().getMessage().contains("BACKBONE_REGISTRATION_CLIENT_SECRET"));
    }

    @Test
    void backboneNamesAnEmptyScope() {
        var configurer = backboneConfigurer();
        ReflectionTestUtils.setField(configurer, "scopes", List.of(" "));

        var failure = assertThrows(RuntimeException.class, () -> configurer.requestInterceptor().apply(new RequestTemplate()));

        assertTrue(failure.getCause().getMessage().contains("BACKBONE_M2M_SCOPES"));
    }

    @Test
    void backboneNamesScopesThatAreNullOrEmpty() {
        for (List<String> scopes : java.util.Arrays.<List<String>>asList(null, List.of())) {
            var configurer = backboneConfigurer();
            ReflectionTestUtils.setField(configurer, "scopes", scopes);

            var failure = assertThrows(RuntimeException.class, () -> configurer.requestInterceptor().apply(new RequestTemplate()));

            assertTrue(failure.getCause().getMessage().contains("BACKBONE_M2M_SCOPES"));
        }
    }

    @Test
    void backboneNamesANullSecret() {
        var configurer = backboneConfigurer();
        ReflectionTestUtils.setField(configurer, "registrationClientSecret", null);

        var failure = assertThrows(RuntimeException.class, () -> configurer.requestInterceptor().apply(new RequestTemplate()));

        assertTrue(failure.getCause().getMessage().contains("BACKBONE_REGISTRATION_CLIENT_SECRET"));
    }

    @Test
    void backboneLogsTheOutgoingHeadersWhenTraceIsEnabled() {
        var configurer = backboneConfigurer();
        ReflectionTestUtils.setField(configurer, "isTraceEnabled", true);
        var template = new RequestTemplate();

        configurer.requestInterceptor().apply(template);

        assertTrue(template.headers().containsKey("Authorization"));
    }

    @Test
    void backboneReportsWhatItRefusedAndWhy() {
        backboneStatus = 400;
        backboneResponse = "{\"status\":400}";
        var configurer = backboneConfigurer();

        var failure = assertThrows(RuntimeException.class, () -> configurer.requestInterceptor().apply(new RequestTemplate()));

        assertTrue(failure.getCause().getMessage().contains("refused the token request"));
        assertTrue(failure.getCause().getMessage().contains("400"));
    }

    @Test
    void backboneRefusesAnEmptyAnswer() {
        backboneStatus = 204;
        backboneResponse = "";
        var configurer = backboneConfigurer();

        assertThrows(RuntimeException.class, () -> configurer.requestInterceptor().apply(new RequestTemplate()));
    }

    @Test
    void backboneAsksAgainWhenTheCachedTokenIsAboutToExpire() {
        var expired = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"exp\":" + (java.time.Instant.now().getEpochSecond() + 10) + "}").getBytes(StandardCharsets.UTF_8));
        backboneResponse = "{\"accessToken\":\"h." + expired + ".s\"}";
        var configurer = backboneConfigurer();

        configurer.requestInterceptor().apply(new RequestTemplate());
        receivedBackboneBody.set(null);
        configurer.requestInterceptor().apply(new RequestTemplate());

        assertTrue(receivedBackboneBody.get() != null);
    }

    @Test
    void feignUsesTheSslBundleWhenThereAreBundles() {
        var bundles = new org.springframework.boot.ssl.DefaultSslBundleRegistry();
        bundles.registerBundle("smartkin-security", org.springframework.boot.ssl.SslBundle.of(org.springframework.boot.ssl.SslStoreBundle.NONE));
        var configurer = backboneConfigurer();
        ReflectionTestUtils.setField(configurer, "sslBundles", bundles);

        assertNotNull(configurer.feignHttpClient());
    }

    @Test
    void feignFallsBackToItsDefaultClientWithoutBundles() {
        assertNotNull(backboneConfigurer().feignHttpClient());
    }

    @Test
    void theTokenRequestUsesTheSslBundleWhenThereAreBundles() {
        var bundles = new org.springframework.boot.ssl.DefaultSslBundleRegistry();
        bundles.registerBundle("smartkin-security", org.springframework.boot.ssl.SslBundle.of(org.springframework.boot.ssl.SslStoreBundle.NONE));
        var configurer = backboneConfigurer();
        ReflectionTestUtils.setField(configurer, "sslBundles", bundles);
        var template = new RequestTemplate();

        configurer.requestInterceptor().apply(template);

        assertEquals(List.of("Bearer abc123"), List.copyOf(template.headers().get("Authorization")));
    }

    @Test
    void theTokenClientsTrustTheInternalCaWithoutAddingABearer() {
        var bundles = new org.springframework.boot.ssl.DefaultSslBundleRegistry();
        bundles.registerBundle("smartkin-security", org.springframework.boot.ssl.SslBundle.of(org.springframework.boot.ssl.SslStoreBundle.NONE));
        var configurer = new TrustedFeignConfigurer();
        ReflectionTestUtils.setField(configurer, "sslBundles", bundles);

        assertNotNull(configurer.feignHttpClient());
        assertNotNull(new TrustedFeignConfigurer().feignHttpClient());
        assertTrue(TrustedFeignConfigurer.class.getDeclaredMethods().length > 0
                && java.util.Arrays.stream(TrustedFeignConfigurer.class.getDeclaredMethods())
                .noneMatch(m -> m.getReturnType().equals(feign.RequestInterceptor.class)));
    }

    @Test
    void bothTokenClientsNameTheTrustedConfiguration() {
        for (Class<?> client : List.of(com.umdc.smartkin.client.backbone.BackboneManagedClient.class,
                com.umdc.smartkin.client.mercury.MercuryAuthClient.class)) {
            var feign = client.getAnnotation(org.springframework.cloud.openfeign.FeignClient.class);
            assertTrue(List.of(feign.configuration()).contains(TrustedFeignConfigurer.class), client.getSimpleName());
        }
    }
}
