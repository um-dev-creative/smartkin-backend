package com.umdc.smartkin.security;

import com.umdc.security.exception.CertificateSecurityException;
import com.umdc.security.jwt.JwtConfigProperties;
import com.umdc.smartkin.security.revocation.RevocationChecker;
import com.umdc.smartkin.security.revocation.SessionValidationUnavailableException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        return serviceWith(secret, 60_000, jti -> false);
    }

    private static SessionJwtServiceImpl serviceWith(String secret, long expirationMs, RevocationChecker checker) {
        var properties = new JwtConfigProperties();
        properties.setSecret(secret);
        properties.setExpirationMs(expirationMs);
        return new SessionJwtServiceImpl(properties, checker);
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

    @Test
    void acceptsAFreshTokenThatIsNotRevoked() {
        assertTrue(service.isValid(service.generateSessionToken("alopez", null)));
    }

    @Test
    void asksTheCheckerWithTheJtiOfTheToken() throws CertificateSecurityException {
        var asked = new AtomicReference<String>();
        var checking = serviceWith(SECRET, 60_000, jti -> {
            asked.set(jti);
            return false;
        });
        var token = checking.generateSessionToken("alopez", null);

        assertTrue(checking.isValid(token));
        assertEquals(checking.getTokenClaims(token).getId(), asked.get());
    }

    @Test
    void rejectsARevokedToken() {
        var revoking = serviceWith(SECRET, 60_000, jti -> true);

        assertFalse(revoking.isValid(revoking.generateSessionToken("alopez", null)));
    }

    @Test
    void rejectsExpiredAndGarbageTokensWithoutAskingTheChecker() {
        RevocationChecker mustNotBeAsked = jti -> {
            throw new AssertionError("The checker must not be consulted");
        };
        var expiring = serviceWith(SECRET, -1_000, mustNotBeAsked);

        assertFalse(expiring.isValid(expiring.generateSessionToken("alopez", null)));
        assertFalse(expiring.isValid("garbage"));
    }

    @Test
    void rejectsATokenWithoutJti() {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        var withoutJti = Jwts.builder().claim("type", "session-token").subject("alopez")
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();

        assertFalse(service.isValid(withoutJti));
    }

    @Test
    void refusesToDecideWhenTheRevocationListIsUnavailable() {
        var unavailable = serviceWith(SECRET, 60_000, jti -> {
            throw new SessionValidationUnavailableException("down", new IllegalStateException());
        });
        var token = unavailable.generateSessionToken("alopez", null);

        assertThrows(SessionValidationUnavailableException.class, () -> unavailable.isValid(token));
    }
}
