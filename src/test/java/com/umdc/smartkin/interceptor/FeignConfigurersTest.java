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
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ClientProperties client(String id) {
        var client = new ClientProperties();
        client.setId(id);
        client.setAuthorizationGrantType("password");
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
        var configurer = new BackboneFeignConfigurer(authProperties());
        var template = new RequestTemplate();

        configurer.requestInterceptor().apply(template);

        assertEquals(List.of("Bearer abc123"), List.copyOf(template.headers().get("Authorization")));
        assertEquals("backbone-client", configurer.getClientId());
        assertTrue(receivedBody.get().contains("client_id=backbone-client"));
        assertTrue(receivedBody.get().contains("grant_type=password"));
    }

    @Test
    void mercuryInterceptorUsesTheMercuryClient() {
        var configurer = new MercuryFeignConfigurer(authProperties());
        ReflectionTestUtils.setField(configurer, "isTraceEnabled", true);
        var template = new RequestTemplate();

        configurer.requestInterceptor().apply(template);

        assertEquals("mercury-client", configurer.getClientId());
        assertTrue(template.headers().containsKey("Authorization"));
        assertTrue(receivedBody.get().contains("client_id=mercury-client"));
    }

    @Test
    void interceptorFailsWhenTheAuthenticatorAnswersWithoutBody() {
        status = 204;
        responseBody = "";
        var configurer = new BackboneFeignConfigurer(authProperties());

        var failure = assertThrows(RuntimeException.class, () -> configurer.requestInterceptor().apply(new RequestTemplate()));

        assertTrue(failure.getCause().getMessage().contains("Manager authenticator"));
    }

    @Test
    void interceptorFailsWhenTheAuthenticatorIsDown() {
        var configurer = new BackboneFeignConfigurer(authProperties());
        server.stop(0);

        assertThrows(RuntimeException.class, () -> configurer.requestInterceptor().apply(new RequestTemplate()));
    }

    @Test
    void interceptorWithoutAMatchingClientFailsFast() {
        var properties = new AuthProperties();
        properties.setClients(List.of(client("other")));
        var configurer = new BackboneFeignConfigurer(properties);

        assertThrows(AssertionError.class, () -> configurer.requestInterceptor().apply(new RequestTemplate()));
    }
}
