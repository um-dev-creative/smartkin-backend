package com.umdc.smartkin.security;

import com.umdc.security.exception.CertificateSecurityException;
import com.umdc.security.jwt.JwtConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionJwtServiceImplTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("a-very-long-secret-key-for-hmac-sha-256!".getBytes(StandardCharsets.UTF_8));

    private SessionJwtServiceImpl service;

    @BeforeEach
    void setUp() {
        service = serviceWith(SECRET);
    }

    private static SessionJwtServiceImpl serviceWith(String secret) {
        var properties = new JwtConfigProperties();
        properties.setSecret(secret);
        properties.setExpirationMs(60_000);
        return new SessionJwtServiceImpl(properties);
    }

    @Test
    void generatesATokenWithTheSubjectAndExtraClaims() throws CertificateSecurityException {
        var token = service.generateSessionToken("alopez", Map.of("vcCompleted", "true"));

        var claims = service.getTokenClaims(token);

        assertEquals("alopez", claims.getSubject());
        assertEquals("true", claims.get("vcCompleted"));
        assertNotNull(claims.getId());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void generatesATokenWhenThereAreNoExtraClaims() throws CertificateSecurityException {
        assertEquals("alopez", service.getTokenClaims(service.generateSessionToken("alopez", null)).getSubject());
        assertEquals("alopez", service.getTokenClaims(service.generateSessionToken("alopez", Map.of())).getSubject());
    }

    @Test
    void readsTheUsernameFromAToken() {
        assertEquals("alopez", service.getUsernameFromToken(service.generateSessionToken("alopez", null)));
    }

    @Test
    void rejectsTokensSignedWithAnotherKey() {
        var otherSecret = Base64.getEncoder()
                .encodeToString("another-long-secret-key-for-hmac-sha-256!".getBytes(StandardCharsets.UTF_8));
        var foreignToken = serviceWith(otherSecret).generateSessionToken("alopez", null);

        assertThrows(CertificateSecurityException.class, () -> service.getTokenClaims(foreignToken));
    }

    @Test
    void rejectsGarbageTokens() {
        assertThrows(CertificateSecurityException.class, () -> service.getTokenClaims("garbage"));
    }
}
